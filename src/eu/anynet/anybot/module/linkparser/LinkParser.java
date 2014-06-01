/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.anynet.anybot.module.linkparser;

import eu.anynet.anybot.bot.ChatMessage;
import eu.anynet.anybot.bot.Module;
import eu.anynet.java.twitter.TwitterApiCredentials;
import eu.anynet.java.util.HTTPConnector;
import eu.anynet.java.util.Regex;
import eu.anynet.java.util.Serializer;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import org.apache.http.HttpResponse;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 * @author sim
 *
 */
public class LinkParser extends Module
{

   private TwitterApiCredentials twittercredentials;


   public LinkParser()
   {

   }


   @Override
   public void launch()
   {
      this.twittercredentials = new TwitterApiCredentials("fillmeout", "fillmeout");
      String fs = this.getProperties().get("fs.settings");
      File tsettings = new File(fs+"twittercredentials.xml");
      Serializer<TwitterApiCredentials> serializer = this.twittercredentials.createSerializer(tsettings);
      if(serializer.isReadyForUnserialize())
      {
         this.twittercredentials = serializer.unserialize();
      }
      else
      {
         this.twittercredentials.serialize();
         this.twittercredentials = null;
      }
   }



   @Override
   public void onMessage(ChatMessage msg)
   {
      if(msg.isChannelMessage())
      {
         String text = msg.getMessage();
         ArrayList<ArrayList<String>> results = Regex.findAllByRegex("(https?://[^\\s]+)[^\\.!#?]?", text);

         if(results.size()>0)
         {
            Iterator<ArrayList<String>> iterator = results.iterator();
            while(iterator.hasNext())
            {
               String url = iterator.next().get(0);
               ParserResult r = null;

               if(r==null && (r = this.parseMetaTags(url)) != null);
               if(r==null && (r = this.parseTwitter(url)) != null);
               if(r==null && (r = this.parseYoutube(url)) != null);

               if(r!=null) {
                  r.build(msg);
               }

            }

         }
      }
   }
   
   private String getPriorityTagContent(Elements sourceelements, ArrayList<HeadTag> searchtags)
   {
      for(Element element : sourceelements)
      {
         for(HeadTag tag : searchtags)
         {
            String checkattribute = tag.getCheckproperty();
            String checkvalue = tag.getCheckvalue();
            String valueattribute = tag.getValueproperty();
            String tagname = tag.getTagname();
            
            if(element.nodeName().equals(tagname) && element.attr(checkattribute)!=null && element.attr(valueattribute)!=null &&
               element.attr(checkattribute).equals(checkvalue))
            {
               return element.attr(valueattribute);
            }
         }
      }
      return null;
   }

   private ParserResult parseMetaTags(String url)
   {
      //if(Regex.isRegexTrue(url, "^http://www\\.golem\\.de/") || Regex.isRegexTrue(url, "^http://www\\.heise\\.de/"))
      //{
      String module = Regex.findByRegexFirst("^https?://(.*?)/", url);
      try {
         HTTPConnector client = new HTTPConnector();
         HttpResponse response = client.doGet(url);
         
         String contenttype = response.getLastHeader("Content-Type").getValue();
         if(!contenttype.startsWith("text/html"))
         {
            return null;
         }
         
         String site = client.responseToString(response);
         client.close();

         ArrayList<HeadTag> metatitlefields = new ArrayList<>();
         metatitlefields.add(HeadTag.metaName("fulltitle"));
         metatitlefields.add(HeadTag.metaName("DC.title"));
         metatitlefields.add(HeadTag.metaProperty("og:title"));

         ArrayList<HeadTag> metadescrfields = new ArrayList<>();
         metadescrfields.add(HeadTag.metaProperty("og:description"));
         metadescrfields.add(HeadTag.metaName("twitter:description"));

         Document sitedocument = Jsoup.parse(site);
         Elements metaelements = sitedocument.select("head meta");

         String title = this.getPriorityTagContent(metaelements, metatitlefields);
         String descr = this.getPriorityTagContent(metaelements, metadescrfields);

         if(title!=null)
         {
            return new ParserResult(module, title, descr);
         }
         
      } catch (Exception ex) { return new ParserResult("linkparser", ex.getMessage()); }
      //}
      return null;
   }

   private ParserResult parseTwitter(String url)
   {
      if(Regex.isRegexTrue(url, "^https://twitter.com/"))
      {
         if(this.twittercredentials==null)
         {
            return new ParserResult("twitter", "Error: No Api Keys found. Please fill twittercredentials.xml file");
         }

         HTTPConnector client = new HTTPConnector();
         try {
            if(!this.twittercredentials.isBearerTokenAvailable())
            {
               this.twittercredentials.generateBearerToken();
            }

            String token = this.twittercredentials.getBearerToken();
            if(token!=null)
            {
               HashMap<String,String> headers = new HashMap<>();
               headers.put("Authorization", "Bearer "+token);
               String result = null;

               //--> Single Tweet: https://twitter.com/twiddern/status/468877914420023296
               result = Regex.findByRegexFirst("^https://twitter\\.com/.*?/status/([0-9]+)$", url);
               if(result!=null)
               {
                  try {
                     JSONObject tweet_json = client.responseToJSONObject(client.doGet("https://api.twitter.com/1.1/statuses/show.json?id="+result, null, headers));
                     if(tweet_json.containsKey("text") && tweet_json.containsKey("user"))
                     {
                        String text = tweet_json.get("text").toString();
                        String user = ((JSONObject)tweet_json.get("user")).get("screen_name").toString();
                        return new ParserResult("twitter", user+": "+text);
                     }
                  } catch(Exception subex) {
                     return new ParserResult("twitter", "Could not get status from twitter RESTful API");
                  }
               }

               //--> User Timeline: https://twitter.com/twiddern
               result = Regex.findByRegexFirst("^https://twitter.com/([^/]+)/?$", url);
               if(result!=null)
               {
                  try {
                     JSONArray tweets = client.responseToJSONArray(client.doGet("https://api.twitter.com/1.1/statuses/user_timeline.json?screen_name="+result+"&count=3", null, headers));

                     if(tweets.size()>0)
                     {
                        ParserResult response = new ParserResult("twitter", "Last 3 tweets of "+result);
                        for(Object otweet : tweets.toArray())
                        {
                           JSONObject tweet = (JSONObject)otweet;
                           response.addLongtext(tweet.get("text").toString().replace("\n", " ").replace("\r", ""));
                        }
                        return response;
                     }
                     else
                     {
                        return new ParserResult("twitter", "No tweets in timeline found");
                     }
                  } catch(Exception subex) {
                     return new ParserResult("twitter", "Could not get user timeline from twitter RESTful API: "+subex.getMessage());
                  }
               }

            }
            else
            {
               return new ParserResult("twitter", "Error: Could not generate bearer token");
            }

         } catch(Exception ex) {
            return new ParserResult("twitter", "Exception: "+ex.getMessage());
         }
         finally
         {
            client.close();
         }
      }
      return null;
   }

   private ParserResult parseYoutube(String url)
   {
      // http://www.youtube.com/watch?v=wcLNteez3c4
      String result = Regex.findByRegexFirst("https?://www\\.youtube\\.com/watch?.*?v=([^&\\s#]+)", url);
      if(result!=null)
      {
         HTTPConnector client = new HTTPConnector();
         try
         {
            JSONObject video = client.responseToJSONObject(client.doGet("http://gdata.youtube.com/feeds/api/videos/"+result+"?v=2&alt=json"));
            if(video.containsKey("entry"))
            {
               String title = (((JSONObject)((JSONObject)video.get("entry")).get("title"))).get("$t").toString();
               return new ParserResult("youtube", title);
            }
            else
            {
               return new ParserResult("youtube", "Video not found");
            }
         }
         catch(Exception ex) {
            return new ParserResult("youtube", "Exception: "+ex.getMessage());
         }
         finally
         {
            client.close();
         }
      }
      return null;
   }


}
