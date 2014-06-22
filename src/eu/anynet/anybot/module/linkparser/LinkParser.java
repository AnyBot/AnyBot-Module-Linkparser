/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.anynet.anybot.module.linkparser;

import eu.anynet.anybot.bot.Module;
import eu.anynet.anybot.pircbotxextensions.MessageEventEx;
import eu.anynet.java.twitter.TwitterApiCredentials;
import eu.anynet.java.util.HttpClient;
import eu.anynet.java.util.Regex;
import eu.anynet.java.util.Serializer;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
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
   public void onMessage(MessageEventEx msg)
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

               this.getBot().sendDebug("[linkparser] Parse URL: "+url);

               if(r==null && (r = this.parseTwitter(url)) != null);
               if(r==null && (r = this.parseYoutube(url)) != null);
               if(r==null && (r = this.parseMetaTags(url)) != null);

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
      String module = Regex.findByRegexFirst("^https?://(.*?)/", url);
      try {
         Response response = HttpClient.Get(url).execute();
         HttpResponse httpresponse = response.returnResponse();
         String contenttype = httpresponse.getLastHeader("Content-Type").getValue();
         if(!contenttype.startsWith("text/html"))
         {
            this.getBot().sendDebug("[linkparser] parseMetaTags: Is not a text/html response");
            return null;
         }

         String site = HttpClient.toString(httpresponse.getEntity().getContent(), 32*1024); // 32KB
         this.getBot().sendDebug("[linkparser] parseMetaTage: "+site.length()+" Byte");

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

      } catch (IOException ex) {
         this.getBot().sendDebug("[linkparser] Metaparser IOException: "+ex.getMessage());
      }
      return null;
   }

   private ParserResult parseTwitter(String url)
   {
      if(Regex.isRegexTrue(url, "^https?://twitter.com/"))
      {
         if(this.twittercredentials==null)
         {
            this.getBot().sendDebug("[linkparser] twitter error: No Api Keys found. Please fill twittercredentials.xml file");
         }

         try {
            if(!this.twittercredentials.isBearerTokenAvailable())
            {
               this.twittercredentials.generateBearerToken();
            }

            String token = this.twittercredentials.getBearerToken();
            if(token!=null)
            {
               String result = null;

               //--> Single Tweet: https://twitter.com/twiddern/status/468877914420023296
               result = Regex.findByRegexFirst("^https://twitter\\.com/.*?/status/([0-9]+)$", url);
               if(result!=null)
               {
                  try
                  {
                     Request request = HttpClient.Get("https://api.twitter.com/1.1/statuses/show.json?id="+result)
                        .addHeader("Authorization", "Bearer "+token);

                     JSONObject tweet_json = HttpClient.toJsonObject(request);
                     if(tweet_json.containsKey("text") && tweet_json.containsKey("user"))
                     {
                        String text = tweet_json.get("text").toString();
                        String user = ((JSONObject)tweet_json.get("user")).get("screen_name").toString();
                        return new ParserResult("twitter", user+": "+text);
                     }
                  } catch(IOException subex) {
                     this.getBot().sendDebug("[linkparser] Twitter IOException: "+subex.getMessage());
                  }
               }

               //--> User Timeline: https://twitter.com/twiddern
               result = Regex.findByRegexFirst("^https://twitter.com/([^/]+)/?$", url);
               if(result!=null)
               {
                  try
                  {
                     Request request = HttpClient.Get("https://api.twitter.com/1.1/statuses/user_timeline.json?screen_name="+result+"&count=3")
                        .addHeader("Authorization", "Bearer "+token);

                     JSONArray tweets = HttpClient.toJsonArray(request);

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
                  } catch(IOException subex) {
                     this.getBot().sendDebug("[linkparser] Twitter IOException: "+subex.getMessage());
                  }
               }

            }
            else
            {
               return new ParserResult("twitter", "Error: Could not generate bearer token");
            }

         } catch(Exception ex) {
            this.getBot().sendDebug("[linkparser] Twitter Exception: "+ex.getMessage());
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
         try
         {
            JSONObject video = HttpClient.toJsonObject(HttpClient.Get("http://gdata.youtube.com/feeds/api/videos/"+result+"?v=2&alt=json"));
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
         catch(IOException ex) {
            this.getBot().sendDebug("[linkparser] Twitter Exception: "+ex.getMessage());
         }
      }
      return null;
   }


}
