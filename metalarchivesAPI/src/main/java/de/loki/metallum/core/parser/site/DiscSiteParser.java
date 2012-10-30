package de.loki.metallum.core.parser.site;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import de.loki.metallum.core.parser.site.helper.ReviewParser;
import de.loki.metallum.core.parser.site.helper.disc.DiscSiteMemberParser;
import de.loki.metallum.core.parser.site.helper.disc.DiscSiteTrackParser;
import de.loki.metallum.core.util.MetallumUtil;
import de.loki.metallum.core.util.net.MetallumURL;
import de.loki.metallum.core.util.net.downloader.Downloader;
import de.loki.metallum.entity.Band;
import de.loki.metallum.entity.Disc;
import de.loki.metallum.entity.Label;
import de.loki.metallum.entity.Review;
import de.loki.metallum.entity.Track;
import de.loki.metallum.enums.DiscType;

public class DiscSiteParser extends AbstractSiteParser<Disc> {

	private boolean			loadReviews	= false;
	private boolean			loadLyrics	= false;
	private static Logger	logger		= Logger.getLogger(DiscSiteParser.class);

	/**
	 * Creates a new DiscParser, just call parse
	 * 
	 * @param disc
	 * @param loadImages
	 * @param loadReviews
	 * @param loadLyrics
	 * @throws ExecutionException
	 */
	public DiscSiteParser(final Disc disc, final boolean loadImages, final boolean loadReviews, final boolean loadLyrics) throws ExecutionException {
		super(disc, loadImages, false);
		this.loadReviews = loadReviews;
		this.loadLyrics = loadLyrics;
	}

	@Override
	public Disc parse() {
		Disc disc = new Disc(this.entity.getId());
		disc.setDiscType(parseDiscType());
		disc.addTracks(parseTracks(disc));
		disc.setName(parseName());
		final String artworkURL = parseArtworkURL();
		disc.setArtworkURL(artworkURL);
		disc.setArtwork(parseDiscArtwork(artworkURL));
		disc.setLabel(parseLabel());
		disc.setDetails(parseDetails());
		disc.setReleaseDate(parseReleaseDate());
		disc = parseMember(disc);
		if (disc.isSplit()) {
			disc.addSplitBand(parseSplitBands());
		} else {
			disc.setBand(parseBand());
		}
		disc.addReview(parseReviewList(disc));
		disc = parseModfications(disc);
		return disc;
	}

	private String parseDetails() {
		if (this.html.contains(" id=\"album_tabs_notes\"")) {
			String details = this.html.substring(this.html.indexOf("id=\"album_tabs_notes\""));
			details = details.substring(details.indexOf("ui-tabs-panel-content"));
			details = details.substring(details.indexOf(">") + 1);
			details = details.substring(0, details.indexOf("</div>")).trim();
			details = MetallumUtil.parseHtmlWithLineSeperators(details);
			return details;
		}
		return "";
	}

	private String parseName() {
		String name = this.html.substring(this.html.indexOf("<h1 class=\"album_name\">"), this.html.indexOf("</h1>"));
		name = name.substring(name.indexOf(">") + 1);
		return Jsoup.parse(name).text();
	}

	private Track[] parseTracks(final Disc disc) {
		DiscSiteTrackParser trackParser = new DiscSiteTrackParser();
		Track[] tracks = trackParser.parse(this.html, disc.isSplit(), this.loadLyrics);
		for (final Track track : tracks) {
			track.setDisc(disc);
		}
		return tracks;
	}

	private DiscType parseDiscType() {
		String details = this.html.substring(this.html.indexOf("<dl class=\"float_left\""));
		String[] discDetails = details.split("<dd>");
		details = discDetails[1].substring(0, discDetails[1].indexOf("</dd>"));
		return DiscType.getTypeDiscTypeForString(details);
	}

	private String parseReleaseDate() {
		String details = this.html.substring(this.html.indexOf("<dl class=\"float_left\""));
		String[] discDetails = details.split("<dd>");
		String date = discDetails[2].substring(0, discDetails[2].indexOf("</dd>"));
		// return MetallumUtil.getMetallumDate(date);
		return date;
	}

	private Label parseLabel() {
		String details = this.html.substring(this.html.indexOf("<dl class=\"float_right\""));
		details = details.substring(details.indexOf(">") + 1, details.indexOf("</dl>"));
		String[] discDetails = details.split("<dd>");
		String labelStr = discDetails[1];
		Label label = new Label(0);
		String labelName = "";
		String labelId = "";
		if (labelStr.contains("</a>")) {
			labelName = labelStr.substring(labelStr.indexOf(">") + 1, labelStr.indexOf("</a>"));
			labelId = labelStr.substring(labelStr.indexOf("/labels/") + 8);
			labelId = labelId.substring(0, labelId.indexOf("#"));
			labelId = labelId.substring(labelId.lastIndexOf("/") + 1, labelId.length());
			if (labelId.contains("#")) {
				labelId = labelId.substring(0, labelId.indexOf("#"));
			}
			label.setId(Long.parseLong(labelId));
		} else {
			labelName = labelStr.substring(0, labelStr.indexOf("</"));

		}
		label.setName(labelName);
		return label;
	}

	private Disc parseMember(final Disc disc) {
		final DiscSiteMemberParser parser = new DiscSiteMemberParser();
		parser.parse(this.html);
		disc.setLineup(parser.getLineupList());
		disc.setGuestLineup(parser.getGuestLineup());
		disc.setMiscLineup(parser.getOtherLinup());
		return disc;
	}

	private Review[] parseReviewList(final Disc disc) {
		final List<Review> reviews = this.entity.getReviews();
		if (!reviews.isEmpty()) {
			final Review[] reviewArr = new Review[reviews.size()];
			return reviews.toArray(reviewArr);
		} else if (this.loadReviews) {
			try {
				ReviewParser parser = new ReviewParser(disc.getId());
				final List<Review> parsedReviewList = parser.parse();
				final Review[] reviewArr = new Review[parsedReviewList.size()];
				for (final Review review : parsedReviewList) {
					review.setDisc(disc);
				}
				return parsedReviewList.toArray(reviewArr);
			} catch (final ExecutionException e) {
				logger.error("unanble to parse reviews from: " + disc, e);
			}
		}
		return new Review[0];
	}

	private final String parseArtworkURL() {
		String artworkURL = null;
		Elements elements = this.doc.getElementsByClass("album_img");
		if (elements.size() > 0) {
			Element imgElement = elements.get(0).select("img[src~=(?i)\\.(png|jpe?g|gif)]").get(0);
			artworkURL = imgElement.attr("src");
		}
		logger.debug("ArtworkURL: " + artworkURL);
		return artworkURL;
	}

	/**
	 * If the previous entity, may from cache, has already the band artwork,
	 * this method will return the BufferedImage of the entity, otherwise if loadImage is true
	 * this method with try to get the Image, if it is in the Metal-Archives, via the Downloader.
	 * 
	 * @return null if loadImage is false or if there is no artwork
	 */
	private final BufferedImage parseDiscArtwork(final String artworkURL) {
		final BufferedImage previouslyParsedArtwork = this.entity.getArtwork();
		if (previouslyParsedArtwork != null) {
			return previouslyParsedArtwork;
		}
		if (this.loadImage && artworkURL != null) {
			try {
				return Downloader.getImage(artworkURL);
			} catch (final ExecutionException e) {
				logger.error("Exception while downloading an image from \"" + artworkURL + "\" ," + this.entity, e);
			}
		}
		// with other words null
		return previouslyParsedArtwork;
	}

	private final Band[] parseSplitBands() {
		Element bandsElement = this.doc.getElementsByClass("band_name").get(0);
		Elements bands = bandsElement.select(("a[href]"));
		Band[] splitBands = new Band[bands.size()];
		for (Element bandElem : bands) {
			String bandLink = bandElem.toString();
			String bandId = bandLink.substring(0, bandLink.indexOf("\">" + bandElem.text()));
			bandId = bandId.substring(bandId.lastIndexOf("/") + 1, bandId.length());
			Band band = new Band(Long.parseLong(bandId), bandElem.text());
			splitBands[bands.indexOf(bandElem)] = band;
		}
		logger.debug("SplitBands: " + splitBands);
		return splitBands;
	}

	private Band parseBand() {
		Band band = new Band(parseBandId(), parseBandName());
		return band;
	}

	private String parseBandName() {
		Element element = this.doc.getElementsByClass("band_name").get(0);
		String bandName = element.text();
		logger.debug("BandName: " + bandName);
		return bandName;
	}

	private long parseBandId() {
		String bandId = this.html.substring(this.html.indexOf("/bands/") + 7);
		bandId = bandId.substring(0, bandId.indexOf("#"));
		bandId = bandId.substring(bandId.lastIndexOf("/") + 1, bandId.length());
		return Long.parseLong(bandId);
	}

	@Override
	protected String getSiteURL() {
		return MetallumURL.assembleDiscURL(this.entity.getId());
	}
}
