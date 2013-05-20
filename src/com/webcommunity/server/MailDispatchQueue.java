package com.webcommunity.server;

import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.webcommunity.shared.bulletinBoard.PostingEntry;
import com.webcommunity.shared.users.UserEntry;

public class MailDispatchQueue {

	private static final Logger log = Logger.getLogger(MailDispatchQueue.class.getName());

	private static final Key ANCHESTOR_KEY = KeyFactory.createKey("PendingMailAnchestor", 9037475439491098755l);
	
	private static final String ENTRY_KIND = "PendingMailEntry";
	private static final String DATE = "date";
	private static final String EMAIL = "email";
	private static final String SUBJECT = "subject";
	private static final String TEXT = "text";
	
	private static DatastoreService datastoreService = null;

	
	public static void enqueuePostingToUsers(PostingEntry postingEntry) {
		try {
			String title = "Nyt på opslagstavlen: " + postingEntry.getTitle();
			String text =  "Gå til http://gf.prezz.net/#BulletinBoard for at læse opslaget med titlen " + postingEntry.getTitle() + ".";
			enqueueMailToUsers(title, text);
		} catch (Exception ex) {
			log.log(Level.SEVERE, "Error enqueing postings to email dispatch queue", ex);
		}
	}
	
	public static void enqueueUserInformationToUsers(UserEntry oldUserEntry, UserEntry newUserEntry) {
		try {
			if (!oldUserEntry.getEmail().equalsIgnoreCase(newUserEntry.getEmail()) || !oldUserEntry.getPhone().equalsIgnoreCase(newUserEntry.getPhone())) {
				String title = "Ændrede kontaktoplysninger: " + newUserEntry.getDisplayName();
				String text =  "Gå til http://gf.prezz.net/#Users for at se de opdaterede kontaktinfomationer for " + newUserEntry.getDisplayName() + ".";
				enqueueMailToUsers(title, text);
			}
		} catch (Exception ex) {
			log.log(Level.SEVERE, "Error enqueing postings to email dispatch queue", ex);
		}
	}

	public static void dispatchPendingMails(int limit) {
		DatastoreService datastore = getDatastoreService();

		Query query = new Query(ENTRY_KIND, ANCHESTOR_KEY);
		query.addSort(DATE, SortDirection.ASCENDING);
		List<Entity> entities = datastore.prepare(query).asList(FetchOptions.Builder.withLimit(limit));
		for (Entity e : entities) {
			String email = null;
			try {
				email = (String)e.getProperty(EMAIL);
				String subject = (String)e.getProperty(SUBJECT);
				String text = (String)e.getProperty(TEXT);
				dispachMail(email, subject, text);
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Error dispaching mail to " + email, ex);
			} finally {
				datastore.delete(e.getKey());
			}
		}
	}
	
	private static void enqueueMailToUsers(String subject, String text) {
		Set<String> trackDublicatesSet = new HashSet<String>();
		
		Date dateNow = Calendar.getInstance().getTime();
		UserEntry[] allUsers = UserManager.getAllUsers();
		for (UserEntry userEntry : allUsers) {
			try {
				if (Boolean.TRUE.equals(userEntry.getSubscribe()) && !trackDublicatesSet.contains(userEntry.getEmail())) {
					InternetAddress emailAddress = createEmailAddress(userEntry.getEmail(), null);
					if (emailAddress != null) {
						trackDublicatesSet.add(userEntry.getEmail());
						enqueueMail(dateNow, userEntry.getEmail(), subject, text);
					}
				}
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Error enqueing mail to user " + userEntry.getUsername(), ex);
			}
		}
	}
	
	private static void enqueueMail(Date date, String email, String subject, String text) {
		Entity entity = new Entity(ENTRY_KIND, ANCHESTOR_KEY);
		entity.setProperty(DATE, date);
		entity.setProperty(EMAIL, email);
		entity.setProperty(SUBJECT, subject);
		entity.setProperty(TEXT, text);

		DatastoreService datastore = getDatastoreService();
		datastore.put(entity);
	}
	
	private static void dispachMail(String email, String subject, String text) throws MessagingException, UnsupportedEncodingException {
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties, null);
        
        Message msg = new MimeMessage(session);
        msg.setFrom(createEmailAddress("administrator@prezz.net", "Grundejerforeningen Søholm"));
        msg.setReplyTo(new Address[] { createEmailAddress("donotreply@prezz.net", null) });
        msg.addRecipient(Message.RecipientType.TO, createEmailAddress(email, null));
        msg.setSubject(MimeUtility.encodeText(subject, "UTF-8", "Q"));
        msg.setContent(text, "text/plain; charset=UTF-8");
        Transport.send(msg);
	}
	
	private static InternetAddress createEmailAddress(String email, String name) {
		InternetAddress result = null;
		if (email != null && !email.isEmpty()) {
			try {
				InternetAddress address = new InternetAddress(email, name, "UTF-8");
				address.validate();
				result = address;
			} catch (Exception ex) {
				log.log(Level.SEVERE, "error generating email address for " + email, ex);
			}
		}
		return result;
	}
	
	private static DatastoreService getDatastoreService() {
		if (datastoreService == null) {
			datastoreService = DatastoreServiceFactory.getDatastoreService();
		}
		
		return datastoreService;
	}	
}
