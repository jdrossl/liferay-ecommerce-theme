/**
 * Copyright (C) 2005-2016 Rivet Logic Corporation.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; version 3 of the License.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package com.rivetlogic.ecommerce.notification.util;

import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.mail.MailMessage;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.util.DateUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portal.util.PortalUtil;
import com.liferay.util.ContentUtil;
import com.liferay.util.mail.MailEngine;
import com.rivetlogic.ecommerce.beans.ShoppingCartPrefsBean;
import com.rivetlogic.ecommerce.cart.ShoppingCartItem;
import com.rivetlogic.ecommerce.cart.ShoppingCartItemUtil;
import com.rivetlogic.ecommerce.model.Notification;
import com.rivetlogic.ecommerce.model.ShoppingOrder;
import com.rivetlogic.ecommerce.notification.constants.NotificationConstants;
import com.rivetlogic.ecommerce.portlet.ShoppingCartPortletConstants;
import com.rivetlogic.ecommerce.service.NotificationLocalServiceUtil;

import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.mail.internet.InternetAddress;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

/**
 * @author isaiulate
 */

public class EmailNotificationUtil {
	
    public static String getPortalLogo(ThemeDisplay themeDisplay) {
        return themeDisplay.getPortalURL() + PortalUtil.getPathImage()
                + "/company_logo?img_id="
                + themeDisplay.getLayoutSet().getLogoId();
    }
    
    public static Message getNotificationMessage(ThemeDisplay themeDisplay, ShoppingOrder shoppingOrder, List<String> cartItemsProductIdList, 
            ShoppingCartPrefsBean cartPrefsBean, String notificationType) throws Exception {
        Message message = new Message();
        message.put(NotificationConstants.CMD, notificationType);
        message.put(NotificationConstants.STORE_EMAIL, cartPrefsBean.getStoreEmail());
        message.put(NotificationConstants.STORE_NAME, cartPrefsBean.getStoreName());
        message.put(NotificationConstants.CUSTOMER_EMAIL, shoppingOrder.getCustomerEmail());
        message.put(NotificationConstants.CUSTOMER_NAME, shoppingOrder.getCustomerName());
        message.put(NotificationConstants.SHOPPING_ORDER, shoppingOrder);
        message.put(NotificationConstants.PORTAL_URL, themeDisplay.getPortalURL());
        message.put(NotificationConstants.PORTAL_LOGO, getPortalLogo(themeDisplay));
        message.put(NotificationConstants.DATE, DateUtil.getDate(new Date(), DATE_FORMAT, Locale.US));

        List<ShoppingCartItem> shoppingCartItems = 
                (themeDisplay.isSignedIn() ? ShoppingCartItemUtil.getCartItems(shoppingOrder.getOrderId(), themeDisplay) : ShoppingCartItemUtil.getCartItemsByProductId(cartItemsProductIdList, themeDisplay));
        if (null != shoppingCartItems) {
            message.put(NotificationConstants.SHOPPING_ORDER_ITEMS, shoppingCartItems);
            double orderTotal = 0l;
            for (ShoppingCartItem shoppingCartItem : shoppingCartItems) {
                orderTotal += Float.valueOf(shoppingCartItem.getPrice())
                        * (float) shoppingCartItem.getCount();
            }
            message.put(NotificationConstants.ORDER_TOTAL, new DecimalFormat(ShoppingCartPortletConstants.DECIMAL_FORMAT).format(orderTotal));
        }

        if (NotificationConstants.STORE_NOTIFICATION.equals(notificationType)) {
            message.put(NotificationConstants.BODY_TEMPLATE, cartPrefsBean.getStoreNotifBodyTemplate());
            message.put(NotificationConstants.SUBJECT_TEMPLATE, cartPrefsBean.getStoreNotifSubjectTemplate());
        } else {
            message.put(NotificationConstants.BODY_TEMPLATE, cartPrefsBean.getCustomerNotifBodyTemplate());
            message.put(NotificationConstants.SUBJECT_TEMPLATE, cartPrefsBean.getCustomerNotifSubjectTemplate());
        }
        return message;
    }
    
	    public static void sendEmailNotification(Message message) throws SystemException {
	    	try{
	    	    Notification notification = processMessage(message);
	    	    sendEmailNotification(notification);
	    	}catch(Exception e){
	    		LOGGER.error(String.format(ERROR_SENDING_NOTIFICATION, message.getString(NotificationConstants.CMD), e.getMessage()));
	    		throw new SystemException(e.getMessage());
	    	}
	    }
	    
	    public static void sendEmailNotification(Notification notification) throws Exception {
	        InternetAddress sender = InternetAddress.parse(notification.getSender())[0],
                    recipient = InternetAddress.parse(notification.getRecipients())[0];
            MailMessage mailMessage = new MailMessage(sender, notification.getSubject(),
                    notification.getBody(), true);
            mailMessage.setTo(recipient);
            MailEngine.send(mailMessage);
	    }
	    
	    public static void storeEmailNotification(long orderId, Message message) {
	        try {
	            Notification notification = processMessage(message);
	            NotificationLocalServiceUtil.storeNotification(notification);
	        } catch (Exception e) {
	            LOGGER.error(e);
	        }
	        
	    }
	    
	    public static void sendEmailNotification(long orderId) {
	        try {
                List<Notification> notifications = NotificationLocalServiceUtil.findByOrderId(orderId);
                for(Notification n : notifications) {
                    sendEmailNotification(n);
                    NotificationLocalServiceUtil.deleteNotification(n);
                }
            } catch (Exception e) {
                LOGGER.error(e);
            }
	    }
	    
	    private static Notification processMessage(Message message) throws Exception{
	    	
	    	Notification notification = null;
	    	ShoppingOrder order = (ShoppingOrder) message.get(NotificationConstants.SHOPPING_ORDER);
	    	
	    	if(NotificationConstants.CUSTOMER_NOTIFICATION.equals(message.getString(NotificationConstants.CMD))){
	    	    notification = NotificationLocalServiceUtil.create(
	    	            order.getOrderId(), 
	    	            message.getString(NotificationConstants.CUSTOMER_EMAIL));
            }else{
                notification = NotificationLocalServiceUtil.create(
                        order.getOrderId(), 
                        message.getString(NotificationConstants.STORE_EMAIL));
            }
	    	
	    	notification.setSender(message.getString(NotificationConstants.STORE_EMAIL));
	    	notification.setSubject(message.getString(NotificationConstants.SUBJECT_TEMPLATE));
	    	notification.setBody(message.getString(NotificationConstants.BODY_TEMPLATE));
    		//messageSender.setHTMLFormat(true);
    		
	    	if(NotificationConstants.CUSTOMER_NOTIFICATION.equals(message.getString(NotificationConstants.CMD))){
	    	    notification.setRecipients(message.getString(NotificationConstants.CUSTOMER_EMAIL));
	    	}else{
	    	    notification.setRecipients(message.getString(NotificationConstants.STORE_EMAIL));
	    	}
    		
    		Map<String, Object> variables = new HashMap<String, Object>();
    		variables.put(NotificationConstants.STORE_EMAIL, message.getString(NotificationConstants.STORE_EMAIL));
    		variables.put(NotificationConstants.STORE_NAME, message.getString(NotificationConstants.STORE_NAME));
    		variables.put(NotificationConstants.CUSTOMER_NAME, message.getString(NotificationConstants.CUSTOMER_NAME));
    		variables.put(NotificationConstants.CUSTOMER_EMAIL, message.getString(NotificationConstants.CUSTOMER_EMAIL));
    		variables.put(NotificationConstants.SHOPPING_ORDER, message.get(NotificationConstants.SHOPPING_ORDER));
    		variables.put(NotificationConstants.SHOPPING_ORDER_ITEMS, message.get(NotificationConstants.SHOPPING_ORDER_ITEMS));
    		variables.put(NotificationConstants.PORTAL_URL, message.get(NotificationConstants.PORTAL_URL));
    		variables.put(NotificationConstants.PORTAL_LOGO, message.get(NotificationConstants.PORTAL_LOGO));
    		variables.put(NotificationConstants.ORDER_TOTAL, message.get(NotificationConstants.ORDER_TOTAL));
    		variables.put(NotificationConstants.DATE, message.get(NotificationConstants.DATE));
    		processTemplates(notification, variables);
    		
	    	return notification;
	    }
	    
	    private static String evaluateTemplate(final Map<String, Object> values, final String emailTemplate) {
	        String result = StringPool.BLANK;
	        try {
	            Velocity.init();
	            StringWriter writer = new StringWriter();
	            Velocity.evaluate(new VelocityContext(values), writer, LOG_TAG_VELOCITY, emailTemplate);
	            result = writer.toString();
	        } catch (Exception e) {
	        	LOGGER.error(String.format(TEMPLATE_PROCESSING_ERROR, emailTemplate, e.getMessage()));
	        }
	       
	        return result;
	    }
	    
	    private static void processTemplates(Notification notification, Map<String, Object> variables) throws Exception {
	    	addCommonTemplates(variables);
	    	notification.setBody(evaluateTemplate(variables, notification.getBody()));
	    	notification.setSubject(evaluateTemplate(variables, notification.getSubject()));
	    	StringBuilder strBuilder = new StringBuilder();
	    	strBuilder.append(ContentUtil.get(NotificationConstants.BODY_HEADER_TEMPLATE));
	    	strBuilder.append(notification.getBody());
	    	strBuilder.append(ContentUtil.get(NotificationConstants.BODY_FOOTER_TEMPLATE));
	    	notification.setBody(strBuilder.toString());
	    }
	    
	    private static void addCommonTemplates(Map<String, Object> messageObjects){
	    	String []templatesNames = getNotificationTemplates();
	    	if(null != templatesNames){
	    		for(int i = 0; i < templatesNames.length; i++){
	    			messageObjects.put(getTemplateVariableName(templatesNames[i]), evaluateTemplate(messageObjects, ContentUtil.get(templatesNames[i])));
	    		}
	    	}
	    	
	    }
	    
	    private static String[] getNotificationTemplates(){
	    	return new String[]{
	    			NotificationConstants.ORDER_SUMMARY_TEMPLATE, 
	    			NotificationConstants.CUSTOMER_INFO_TEMPLATE,
	    			NotificationConstants.STORE_LOGO_TEMPLATE,
	    			NotificationConstants.ORDER_TOTAL_TEMPLATE};
	    }
	    
	    private static String getTemplateVariableName(String templatePath){
	    	switch(templatePath){
		    	case NotificationConstants.ORDER_SUMMARY_TEMPLATE:{
		    		return NotificationConstants.ORDER_SUMMARY;
		    	}
		    	case NotificationConstants.CUSTOMER_INFO_TEMPLATE:{
		    		return NotificationConstants.CUSTOMER_INFO;
		    	}
		    	case NotificationConstants.STORE_LOGO_TEMPLATE:{
		    		return NotificationConstants.STORE_LOGO;
		    	}
		    	case NotificationConstants.ORDER_TOTAL_TEMPLATE:{
		    		return NotificationConstants.ORDER_TOTAL;
		    	}
		    	default:
		    		return null;
	    	}
	    }
	    
	    private static final String LOG_TAG_VELOCITY = "velocityLogTag";
		private static final Log LOGGER = LogFactoryUtil.getLog(EmailNotificationUtil.class);
		private static final String TEMPLATE_PROCESSING_ERROR = "Error while processing an email velocity template. Template value: %S. %S";
		private static final String ERROR_SENDING_NOTIFICATION = "Error while sending a notification. Notification Type: %S. %S";
		private static final String DATE_FORMAT = "EEE, MMM d, yyyy ha";
}
