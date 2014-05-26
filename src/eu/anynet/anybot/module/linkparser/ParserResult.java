/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.anynet.anybot.module.linkparser;

import eu.anynet.anybot.bot.ChatMessage;
import java.util.ArrayList;
import org.apache.commons.lang3.StringEscapeUtils;

/**
 *
 * @author sim
 */
public class ParserResult
{
   public ParserResult(String module, String title, String longtext)
   {
      this.module = StringEscapeUtils.unescapeHtml4(module);
      this.title = StringEscapeUtils.unescapeHtml4(title);
      this.longtext = new ArrayList<>();
      this.longtext.add(longtext);
   }

   public ParserResult(String module, String title)
   {
      this(module, title, null);
   }

   private String module;
   private String title;
   private ArrayList<String> longtext;

   public void build(ChatMessage msg)
   {
      String headline = "["+module+"] "+title;
      msg.respond(headline);
      if(longtext!=null && longtext.size()>0)
      {
         for(Object temp : longtext.toArray())
         {
            if(temp!=null)
            {
               msg.respond(temp.toString());
            }
         }
      }
   }

   public void addLongtext(String text)
   {
      if(text!=null && text.length()>0)
      {
         this.longtext.add(StringEscapeUtils.unescapeHtml4(text.trim()));
      }
   }
}
