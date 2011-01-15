package controllers;

import models.Announcement;
import models.Event;
import models.Participant;
import models.LectureHolder;
import notifiers.MailMan;
import org.apache.abdera.Abdera;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.Parser;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateMidnight;
import play.cache.Cache;
import play.data.validation.Email;
import play.data.validation.Required;
import play.libs.Codec;
import play.libs.Crypto;
import play.libs.Images;
import play.mvc.Controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class Application extends Controller {

    private static final String URL_NEWS_RSS = "http://wiki.java.no/spaces/createrssfeed.action?types=blogpost&spaces=forside&maxResults=10&title=[Forsiden]+News+Feed&publicFeed=true&labelString=forside&showContent=true&showDiff=true&rssType=atom&timeSpan=5";
	private static final String BASE_URL_FLAT_PAGES = "http://dav.java.no/forside_statisk_test/";

    private static ConfluencePageFetcher fetcher = new ConfluencePageFetcher();

	public static void index() {
        List<Announcement> announcements;

        announcements = getAnnouncements();

        List<Event> events = Event.find("published is true and date >= ? order by date asc", new DateMidnight().plus(1).toDate()).fetch();
        String randomId = Codec.UUID();
        render(announcements, events, randomId);
    }


    private static List<Announcement> getAnnouncements() {
        List<Announcement> announcements;
        Abdera abdera;
        Parser parser;
        URL url;
        Document<Feed> doc;
        Feed feed;

        announcements = new LinkedList<Announcement>();
        abdera = new Abdera();
        parser = abdera.getParser();

        try {
            url = new URL(URL_NEWS_RSS);

            doc = parser.parse(url.openStream());
            feed = doc.getRoot();

            for (Entry entry : feed.getEntries()) {
                announcements.add(new Announcement(entry.getTitle(), entry.getSummary(), entry.getLink("alternate").getHref().toString()));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return announcements;
    }


    public static void signUpForEvent(Long eventId, String randomId, String code, @Required @Email String email, @Required String name, @Required Integer howMany) {
        validation.equals(code.toLowerCase(), Cache.get(randomId)).message("Feil kode!");
        validation.match(howMany, "[1-9]").message("Feltet må være et siffer mellom 1 og 9");
        if (!validation.hasErrors()) {
            Participant participant = null;
            List<Participant> participantList = Participant.find("email = ?", email).fetch();
            if (participantList == null || participantList.isEmpty()) {
                participant = new Participant(email, name);
            } else {
                participant = participantList.get(0);
            }

            Event event = Event.findById(eventId);
            if (!event.participants.contains(participant)) {
                event.participants.add(participant);
                event.participantCount += howMany;
                event.save();
                String crypto = Crypto.encryptAES(participant.id + "_" + event.id);
                MailMan.signUp(participant, event, crypto);
            }
        } else {
            params.flash();
            renderJSON(validation); // gi tilbakemelding.
        }
        Cache.delete(randomId);
        renderJSON(validation); // be bruker sjekke postkassa si.
    }

    public static void regretSigningUp(String id) {
        String decrypted = Crypto.decryptAES(id);
        String[] strings = StringUtils.split(decrypted, '_');
        if(strings != null && strings.length == 2){
            Participant participant = Participant.findById(Long.parseLong(strings[0]));
            Event event = Event.findById(Long.parseLong(strings[1]));

            if (event != null && participant != null && event.participants.contains(participant)) {
                event.participants.remove(participant);
                event.save();
                render(participant, event);
            }

        }
        render();
    }

    public static void listOldEvents() {
        Date today = new DateMidnight().toDate();
        List<Event> osloEvents = Event.find("published is true and region = ? and date < ? ", Event.Region.OSLO, today).fetch();
        List<Event> trondheimEvents = Event.find("published is true and region = ? and date < ?", Event.Region.TRONDHEIM, today).fetch();
        List<Event> sorlandetEvents = Event.find("published is true and region = ? and date < ?", Event.Region.SORLANDET, today).fetch();
        List<Event> bergenEvents = Event.find("published is true and region = ? and date < ?", Event.Region.BERGEN, today).fetch();
        List<Event> stavangerEvents = Event.find("published is true and region = ? and date < ?", Event.Region.STAVANGER, today).fetch();
        render(osloEvents, trondheimEvents, sorlandetEvents, bergenEvents, stavangerEvents);
    }

    public static void captcha(String id) {
        Images.Captcha captcha = Images.captcha();
        String code = captcha.getText("#000000");
        Cache.set(id, code.toLowerCase(), "10mn");
        renderBinary(captcha);
    }


    public static void lectureholders() {
        List<LectureHolder> lectureholders = LectureHolder.findAll();
		render(lectureholders);
    }

    public static void contact() {
        render();
    }

    public static void confluence(String name) {
        String document = fetcher.getPageAsHTMLFragment(name);
        render(document);
    }

    public static String gravatarhash(String input){
        if(input != null)
          return Codec.hexMD5(input.toLowerCase().trim());
        else
            return null;
    }


}
