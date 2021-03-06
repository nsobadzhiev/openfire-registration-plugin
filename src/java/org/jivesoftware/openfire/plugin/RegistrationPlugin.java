/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.plugin;

import java.io.File;
import java.util.*;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.privacy.PrivacyList;
import org.jivesoftware.openfire.privacy.PrivacyListManager;
import org.jivesoftware.util.EmailService;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;


/**
 * Registration plugin.
 *
 * @author Ryan Graham.
 */
public class RegistrationPlugin implements Plugin {
    
    private static final Logger Log = LoggerFactory.getLogger(RegistrationPlugin.class);
    
    private static final String URL = "registration/sign-up.jsp";
   
    /**
     * The expected value is a boolean, if true all contacts specified in the property #IM_CONTACTS
     * will receive a notification when a new user registers. The default value is false.
     */
    private static final String IM_NOTIFICATION_ENABLED = "registration.imnotification.enabled";
    
    /**
     * The expected value is a boolean, if true all contacts specified in the property #EMAIL_CONTACTS 
     * will receive a notification when a new user registers. The default value is false.
     */
    private static final String EMAIL_NOTIFICATION_ENABLED = "registration.emailnotification.enabled";
    
    /**
     * The expected value is a boolean, if true any user who registers will receive the welcome 
     * message specified in the property #WELCOME_MSG. The default value is false.
     */
    private static final String WELCOME_ENABLED = "registration.welcome.enabled";
    
    /**
     * The expected value is a boolean, if true any user who registers will be added to the group 
     * specified in the property #REGISTRAION_GROUP. The default value is false.
     */
    private static final String GROUP_ENABLED = "registration.group.enabled";
    
    /**
     * The expected value is a boolean, if true any user who registers will have a Default 
     * privacy list specified in the property #REGISTRAION_PRIVACYLIST. The default value is false.
     */
    private static final String PRIVACYLIST_ENABLED = "registration.privacylist.enabled";
    
    /**
     * The expected value is a boolean, if true any users will be able to register at the following
     * url http://[SERVER_NAME}:9090/plugins/registration/sign-up.jsp
     */
    private static final String WEB_ENABLED = "registration.web.enabled";
    
    /**
      * The expected value is a boolean, if true any users will be need to verify its a human at the
     * following url http://[SERVER_NAME}:9090/plugins/registration/sign-up.jsp
     */
    private static final String RECAPTCHA_ENABLED = "registration.recaptcha.enabled";
    
    /**
     * The expected value is a boolean, if true recaptcha uses the noscript tag.
     */
    private static final String RECAPTCHA_NOSCRIPT = "registration.recaptcha.noscript";
    
    /**
     * The expected value is a String that contains the public key for the recaptcha login.
     */
    private static final String RECAPTCHA_PUBLIC_KEY = "registration.recaptcha.key.public";
    
    /**
     * The expected value is a String that contains the private key for the recaptcha login.
     */
    private static final String RECAPTCHA_PRIVATE_KEY = "registration.recaptcha.key.private";
    
    /**
     * The expected value is a comma separated String of usernames who will receive a instant
     * message when a new user registers if the property #IM_NOTIFICATION_ENABLED is set to true.
     */
    private static final String IM_CONTACTS = "registration.notification.imContacts";
    
    /**
     * The expected value is a comma separated String of email addresses who will receive an email
     * when a new user registers, if the property #EMAIL_NOTIFICATION_ENABLED is set to true.
     */
    private static final String EMAIL_CONTACTS = "registration.notification.emailContacts";
    
    /**
     * The expected value is a String that contains the message that will be sent to a new user
     * when they register, if the property #WELCOME_ENABLED is set to true.
     */
    private static final String WELCOME_MSG = "registration.welcome.message";

    /**
     * The expected value is a String that contains the raw XMPP message that will be sent to a new user
     * when they register, if the property #WELCOME_ENABLED is set to true.
     */
    private static final String WELCOME_RAW_MSG = "registration.welcome.message.raw";

    /**
     * The expected value is a String that contains the JID of the account sending the
     * welcome message. Defaults to the server itself
     */
    private static final String WELCOME_MSG_FROM = "registration.welcome.message.from";
    
    /**
     * The expected value is a String that contains the name of the group that a new user will 
     * be added to when they register, if the property #GROUP_ENABLED is set to true.
     */
    private static final String REGISTRAION_GROUP = "registration.group";
    
    /**
     * The expected value is a String that contains the XML contents of the default
     * privacy list, if the property #PRIVACYLIST_ENABLED is set to true.
     */
    private static final String REGISTRAION_PRIVACYLIST = "registration.privacylist";
    
    /**
     * The expected value is a String that contains the name of the default
     * privacy list, if the property #PRIVACYLIST_ENABLED is set to true.
     */
    private static final String REGISTRAION_PRIVACYLIST_NAME = "registration.privacylist.name";

    /**
     * The expected value is a numeric (long) value that defines the number of seconds after which
     * a newly created User will be automatically locked out. A non-positive value (zero or less) will
     * disable this feature (it is disabled by default).
     */
    private static final String REGISTRATION_AUTO_LOCKOUT = "registration.automatic.lockout.seconds";

    /**
     * The expected value is a String that contains the text that will be displayed in the header
     * of the sign-up.jsp, if the property #WEB_ENABLED is set to true.
     */
    private static final String HEADER = "registration.header";

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationPlugin.class);

    private RegistrationUserEventListener listener = new RegistrationUserEventListener();
    
    private String serverName;
    private JID serverAddress;
    private MessageRouter router;
    private boolean privacyListCacheIsSet = false;
    private Element privacyListCache = null;
    
    private List<String> imContacts = new ArrayList<String>();
    private List<String> emailContacts = new ArrayList<String>();
    
    public RegistrationPlugin() {
        serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        serverAddress = new JID(serverName);
        router = XMPPServer.getInstance().getMessageRouter();
       
        String imcs = JiveGlobals.getProperty(IM_CONTACTS);
        if (imcs != null) {
             imContacts.addAll(Arrays.asList(imcs.split(",")));
        }
         
        String ecs = JiveGlobals.getProperty(EMAIL_CONTACTS);
        if (ecs != null) {
            emailContacts.addAll(Arrays.asList(ecs.split(",")));
        }
                
        UserEventDispatcher.addListener(listener);
        
        //delete properties from version 1.0
        JiveGlobals.deleteProperty("registration.notification.contact");
        JiveGlobals.deleteProperty("registration.notification.enabled");
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        AuthCheckFilter.addExclude(URL);
    }

    public void destroyPlugin() {
        AuthCheckFilter.removeExclude(URL);
        UserEventDispatcher.removeListener(listener);
        serverAddress = null;
        listener = null;
        router = null;
    }
    
    public void setIMNotificationEnabled(boolean enable) {
        JiveGlobals.setProperty(IM_NOTIFICATION_ENABLED, enable ? "true" : "false");
    }
    
    public boolean imNotificationEnabled() {
        return JiveGlobals.getBooleanProperty(IM_NOTIFICATION_ENABLED, false);
    }
    
    public void setEmailNotificationEnabled(boolean enable) {
        JiveGlobals.setProperty(EMAIL_NOTIFICATION_ENABLED, enable ? "true" : "false");
    }
    
    public boolean emailNotificationEnabled() {
        return JiveGlobals.getBooleanProperty(EMAIL_NOTIFICATION_ENABLED, false);
    }
    
    public Collection<String> getIMContacts() {
        Collections.sort(imContacts);
        return imContacts;
    }
   
    public void addIMContact(String contact) {
        if (!imContacts.contains(contact.trim().toLowerCase())) {
            imContacts.add(contact.trim().toLowerCase());
            JiveGlobals.setProperty(IM_CONTACTS, propPrep(imContacts));
        }
    }

    public void removeIMContact(String contact) {
        imContacts.remove(contact.trim().toLowerCase());
        if (imContacts.size() == 0) {
            JiveGlobals.deleteProperty(IM_CONTACTS);
        }
        else {
            JiveGlobals.setProperty(IM_CONTACTS, propPrep(imContacts));
        }
    }

    public Collection<String> getEmailContacts() {
        Collections.sort(emailContacts);
        return emailContacts;
    }

    public void addEmailContact(String contact) {
        if (!emailContacts.contains(contact.trim())) {
            emailContacts.add(contact.trim());
            JiveGlobals.setProperty(EMAIL_CONTACTS, propPrep(emailContacts));
        }
    }

    public void removeEmailContact(String contact) {
        emailContacts.remove(contact);
        if (emailContacts.size() == 0) {
            JiveGlobals.deleteProperty(EMAIL_CONTACTS);
        }
        else {
            JiveGlobals.setProperty(EMAIL_CONTACTS, propPrep(emailContacts));
        }
    }
    
    public void setWelcomeEnabled(boolean enable) {
        JiveGlobals.setProperty(WELCOME_ENABLED, enable ? "true" : "false");
    }
   
    public boolean welcomeEnabled() {
        return JiveGlobals.getBooleanProperty(WELCOME_ENABLED, false);
    }

    public void setWelcomeMessage(String message) {
        JiveGlobals.setProperty(WELCOME_MSG, message);
    }

    public void setWelcomeRawMessage(String message) {
        JiveGlobals.setProperty(WELCOME_RAW_MSG, message);
    }

    public void setWelcomeMessageFrom(String from) {
        JiveGlobals.setProperty(WELCOME_MSG_FROM, from);
    }

    public String getWelcomeMessage() {
        return JiveGlobals.getProperty(WELCOME_MSG, "Welcome to Openfire!");
    }

    public String getWelcomeRawMessage() {
        return JiveGlobals.getProperty(WELCOME_RAW_MSG);
    }

    public String getWelcomeMessageFrom() {
        return JiveGlobals.getProperty(WELCOME_MSG_FROM);
    }
    
    public void setGroupEnabled(boolean enable) {
        JiveGlobals.setProperty(GROUP_ENABLED, enable ? "true" : "false");
    }
    
    public boolean groupEnabled() {
        return JiveGlobals.getBooleanProperty(GROUP_ENABLED, false);
    }
    public void setPrivacyListEnabled(boolean enable) {
        JiveGlobals.setProperty(PRIVACYLIST_ENABLED, enable ? "true" : "false");
    }
    
    public boolean privacyListEnabled() {
        return JiveGlobals.getBooleanProperty(PRIVACYLIST_ENABLED, false);
    }
    
    public void setWebEnabled(boolean enable) {
        JiveGlobals.setProperty(WEB_ENABLED, enable ? "true" : "false");
    }
   
    public boolean webEnabled() {
        return JiveGlobals.getBooleanProperty(WEB_ENABLED, false);
    }
    
    public String webRegistrationAddress() {
        return  "http://" + XMPPServer.getInstance().getServerInfo().getXMPPDomain() + ":"
            + JiveGlobals.getXMLProperty("adminConsole.port") + "/plugins/" + URL;
    }
    
    public void setReCaptchaEnabled(boolean enable) {
        JiveGlobals.setProperty(RECAPTCHA_ENABLED, enable ? "true" : "false");
    }
    
    public boolean reCaptchaEnabled() {
        return JiveGlobals.getBooleanProperty(RECAPTCHA_ENABLED, false);
    }
    
    public void setReCaptchaNoScript(boolean enable) {
        JiveGlobals.setProperty(RECAPTCHA_NOSCRIPT, enable ? "true" : "false");
    }
    
    public boolean reCaptchaNoScript() {
        return JiveGlobals.getBooleanProperty(RECAPTCHA_NOSCRIPT, true);
    }
    
    public void setReCaptchaPublicKey(String publicKey) {
        JiveGlobals.setProperty(RECAPTCHA_PUBLIC_KEY, publicKey);
    }
    
    public String getReCaptchaPublicKey() {
        return JiveGlobals.getProperty(RECAPTCHA_PUBLIC_KEY);
    }
    
    public void setReCaptchaPrivateKey(String privateKey) {
        JiveGlobals.setProperty(RECAPTCHA_PRIVATE_KEY, privateKey);
    }
    
    public String getReCaptchaPrivateKey() {
        return JiveGlobals.getProperty(RECAPTCHA_PRIVATE_KEY);
    }
    
    public void setGroup(String group) {
        JiveGlobals.setProperty(REGISTRAION_GROUP, group);
    }
    
    public String getGroup() {
        return JiveGlobals.getProperty(REGISTRAION_GROUP);
    }
    
    public void setPrivacyList(String privacyList) {
        JiveGlobals.setProperty(REGISTRAION_PRIVACYLIST, privacyList);
        privacyListCacheIsSet = false;
    }
    
    public String getPrivacyList() {
        return JiveGlobals.getProperty(REGISTRAION_PRIVACYLIST);
    }
    
    public void setPrivacyListName(String privacyListName) {
        JiveGlobals.setProperty(REGISTRAION_PRIVACYLIST_NAME, privacyListName);
    }
    
    public String getPrivacyListName() {
        return JiveGlobals.getProperty(REGISTRAION_PRIVACYLIST_NAME);
    }

    public boolean isAutomaticAccountLockoutEnabled()
    {
        return getAutomaticAccountLockoutAfter() > 0;
    }

    public void setAutomaticAccountLockoutAfter( long seconds )
    {
        JiveGlobals.setProperty( REGISTRATION_AUTO_LOCKOUT, Long.toString( seconds ) );
    }
    public long getAutomaticAccountLockoutAfter()
    {
        return JiveGlobals.getLongProperty( REGISTRATION_AUTO_LOCKOUT, -1 );
    }

    public void setHeader(String message) {
        JiveGlobals.setProperty(HEADER, message);
    }

    public String getHeader() {
        return JiveGlobals.getProperty(HEADER, "Web Sign-In");
    }
    
    private class RegistrationUserEventListener implements UserEventListener {
        public void userCreated(User user, Map<String, Object> params) {
            
            if (Log.isDebugEnabled()) {
                Log.debug("Registration plugin : registering new user");
            }
                
            if (imNotificationEnabled()) {
                sendIMNotificatonMessage(user);
            }
            
            if (emailNotificationEnabled()) {
                sendAlertEmail(user);
            }
            
            if (welcomeEnabled()) {
                try {
                    sendWelcomeMessage(user);
                } catch (DocumentException e) {
                    Log.error("Unable to convert welcome text into Message");
                    e.printStackTrace();
                }
            }
            
            if (groupEnabled()) {
                addUserToGroup(user);
            }
            
            if (privacyListEnabled()) {
                addDefaultPrivacyList(user);
            }

            if (isAutomaticAccountLockoutEnabled())
            {
                addAutomaticAccountLockout(user);
            }
        }

        public void userDeleting(User user, Map<String, Object> params) {
        }

        public void userModified(User user, Map<String, Object> params) {
        }
        
        private void sendIMNotificatonMessage(User user) {
            String msg = " A new user with the username '" + user.getUsername() + "' just registered.";
            
            for (String contact : getIMContacts()) {
                router.route(createServerMessage(contact + "@" + serverName,
                            "Registration Notification", msg));
            }
        }
        
        private void sendAlertEmail(User user) {
            String subject = "User Registration";
            String body = " A new user with the username '" + user.getUsername() + "' just registered.";
            
            EmailService emailService = EmailService.getInstance();
            for (String toAddress : emailContacts) {
               try {
                   emailService.sendMessage(null, toAddress, "Openfire", "no_reply@" + serverName,
                           subject, body, null);
               }
               catch (Exception e) {
                   Log.error(e.getMessage(), e);
               }
           }
        }

        private void sendWelcomeMessage(User user) throws DocumentException {
            String rawWelcomeMessage = getWelcomeRawMessage();
            List<Message> welcomeMessages = new ArrayList();
            String to = user.getUsername() + "@" + serverName;
            if (rawWelcomeMessage != null && !rawWelcomeMessage.isEmpty()) {
                welcomeMessages = createServerMessage(to, rawWelcomeMessage);
            } else {
                welcomeMessages.add(createServerMessage(to, "Welcome", getWelcomeMessage()));
            }
            welcomeMessages.forEach(router::route);
        }
        
        private Message createServerMessage(String to, String subject, String body) {
            Message message = new Message();
            message.setTo(to);
            message.setFrom(serverAddress);
            if (subject != null) {
                message.setSubject(subject);
            }
            message.setBody(body);
            return message;
        }

        private List<Message> createServerMessage(String to, String rawMessage) throws DocumentException {
            Document document = DocumentHelper.parseText(rawMessage);
            // It could be a single message or an array of messages
            // An array of messages has "messages" as a root element
            List<Message> messages = new ArrayList();
            JID from = getWelcomeMessageFrom() != null ? new JID(getWelcomeMessageFrom()) : serverAddress;
            if (document.getRootElement().getName().equals("messages")) {
                for (Iterator i = document.getRootElement().elementIterator(); i.hasNext();) {
                    Element element = (Element) i.next();
                    messages.add(messageFromElement(element, to, from));
                }
            } else {
                messages.add(messageFromElement(document.getRootElement(), to, from));
            }
            return messages;
        }

        private Message messageFromElement(Element element, String to, JID from) {
            Message message = new Message(element);
            message.setTo(to);
            message.setFrom(from);
            return message;
        }
        
        private void addUserToGroup(User user) {
            try {
                GroupManager groupManager =  GroupManager.getInstance();
                Group group = groupManager.getGroup(getGroup());
                group.getMembers().add(XMPPServer.getInstance().createJID(user.getUsername(), null));
            }
            catch (GroupNotFoundException e) {
                Log.error(e.getMessage(), e);
            }
        }
        
        private void addDefaultPrivacyList(User user) {
            
            if (Log.isDebugEnabled()) {
                Log.debug("Registration plugin : adding default privacy list.");
                Log.debug("\tName = "+getPrivacyListName());
                Log.debug("\tContent = "+getPrivacyList());
            }
            
            if(!privacyListCacheIsSet) {
                privacyListCacheIsSet = true;
                try {
                    Document document = DocumentHelper.parseText(getPrivacyList());
                    privacyListCache = document.getRootElement();
                }
                catch (DocumentException e) {
                    Log.error(e.getMessage(), e);
                }
                if(privacyListCache == null) { 
                    Log.error("registration.privacylist can not be parsed into a valid privacy list");
                }
            }
            if(privacyListCache != null) {
                PrivacyListManager privacyListManager = PrivacyListManager.getInstance();
                PrivacyList newPrivacyList = privacyListManager.createPrivacyList(user.getUsername(), getPrivacyListName(), privacyListCache);
                privacyListManager.changeDefaultList(user.getUsername(), newPrivacyList, null);
            }
        }

        private void addAutomaticAccountLockout(User user)
        {
            final long start = System.currentTimeMillis() + ( getAutomaticAccountLockoutAfter() * 1000 );
            LockOutManager.getInstance().disableAccount( user.getUsername(), new Date( start ), null );
        }
    }
    
    private String propPrep(Collection<String> props) {
        StringBuilder buf = new StringBuilder();
        Iterator<String> iter = props.iterator();
        while (iter.hasNext()) {
            String con = iter.next();
            buf.append(con);
            
            if (iter.hasNext()) {
                buf.append(",");
            }
        }
        return buf.toString();
    }
    
    public boolean isValidAddress(String address) {
        if (address == null) {
            return false;
        }

        // Must at least match x@x.xx. 
        return address.matches(".{1,}[@].{1,}[.].{2,}");
    }
}
