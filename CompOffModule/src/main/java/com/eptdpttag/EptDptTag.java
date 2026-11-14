/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.eptdpttag;

import com.util.CipherText;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;

/**
 *
 * @author IGS
 */
public class EptDptTag extends TagSupport
{
    private String data;
    private String type;
    private CipherText en;

    @Override
    public int doStartTag() throws JspException
    {
        JspWriter out=pageContext.getOut();
        try
        {
            en=new CipherText();
          //  System.out.println("EptDptTag type===>"+type);
          //  System.out.println("EptDptTag data===>"+data);
            if(type.equals("EPT") && !data.isEmpty() && !data.equals(" ") && !data.equalsIgnoreCase("null"))
            {
                out.print(en.encrypt(data.trim()));
            }
            if(type.equals("DPT") && !data.isEmpty() && !data.equals(" ") && !data.equalsIgnoreCase("null"))
            {
            	String dt=en.decrypt(data.trim());
                out.print(org.apache.commons.lang.StringEscapeUtils.escapeHtml(dt));
               //out.print(en.decrypt(data));
            }
        }
        catch(Exception e)
        {
            System.out.println("EptDptTag exception===>"+e);
        }
        return SKIP_BODY;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
    public CipherText getEn() {
        return en;
    }

    public void setEn(CipherText en) {
        this.en = en;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
