/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.anynet.anybot.module.linkparser;

/**
 *
 * @author sim
 */
public class HeadTag
{

   public String tagname;
   public String checkproperty;
   public String valueproperty;
   public String checkvalue;

   public HeadTag(String tag, String checkproperty, String valueproperty, String checkvalue)
   {
      this.tagname = tag;
      this.checkproperty = checkproperty;
      this.valueproperty = valueproperty;
      this.checkvalue = checkvalue;
   }

   public static HeadTag metaProperty(String checkvalue)
   {
      return new HeadTag("meta", "property", "content", checkvalue);
   }

   public static HeadTag metaName(String checkvalue)
   {
      return new HeadTag("meta", "name", "content", checkvalue);
   }

   public String getCheckvalue() {
      return checkvalue;
   }

   public void setCheckvalue(String checkvalue) {
      this.checkvalue = checkvalue;
   }

   public String getTagname() {
      return tagname;
   }

   public void setTagname(String tagname) {
      this.tagname = tagname;
   }

   public String getCheckproperty() {
      return checkproperty;
   }

   public void setCheckproperty(String checkproperty) {
      this.checkproperty = checkproperty;
   }

   public String getValueproperty() {
      return valueproperty;
   }

   public void setValueproperty(String valueproperty) {
      this.valueproperty = valueproperty;
   }

}
