// Copyright 2006 Konrad Twardowski
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.makagiga.feeds.atom10;

import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlValue;

import org.makagiga.commons.MDate;
import org.makagiga.commons.TK;
import org.makagiga.feeds.AbstractItem;
import org.makagiga.feeds.Enclosure;
import org.makagiga.feeds.archive.ArchiveItem;

public class Atom10Item extends AbstractItem {
	
	// protected

	/**
	 * @since 3.2
	 */
	@XmlElement(name = "link", namespace = "http://www.w3.org/2005/Atom")
	protected List<Link> links;
	
	// private
	
	@XmlElement(name = "title", namespace = "http://www.w3.org/2005/Atom")
	private Title title;
	
	// public
	
	public Atom10Item() { }

	@Override
	@XmlTransient
	public ArchiveItem.DateFormat getArchiveDateFormat() { return ArchiveItem.DateFormat.RFC_3339; };

	@Override
	public java.util.Date getPublishedDateAsDate() {
		return MDate.parseRFC3339(getPublishedDate());
	}

	@Override
	public java.util.Date getUpdatedDateAsDate() {
		return MDate.parseRFC3339(getUpdatedDate());
	}

	@Override
	@XmlElement(name = "summary", namespace = "http://www.w3.org/2005/Atom")
	public void setDescription(final String value) {
		super.setDescription(value);
	}

	@Override
	@XmlElement(name = "id", namespace = "http://www.w3.org/2005/Atom")
	public void setId(final String value) {
		super.setId(value);
	}

// TODO: 2.2: "<content type="application/xhtml+xml"
	@Override
	@XmlElement(name = "content", namespace = "http://www.w3.org/2005/Atom")
	public void setLongDescription(final String value) {
		super.setLongDescription(value);
	}

	@Override
	@XmlElement(name = "published", namespace = "http://www.w3.org/2005/Atom")
	public void setPublishedDate(final String value) {
		super.setPublishedDate(value);
	}

/*
	@Override
	@XmlElement(name = "title", namespace = "http://www.w3.org/2005/Atom")
	public void setTitle(final String value) {
		super.setTitle(value);
	}
*/

	@Override
	@XmlElement(name = "updated", namespace = "http://www.w3.org/2005/Atom")
	public void setUpdatedDate(final String value) {
		super.setUpdatedDate(value);
	}

	// protected
	
	@Override
	protected void onFinish() {
		Link commentsURL = null;
		Link storyURL = null;
		if (!TK.isEmpty(links)) {
			for (Link i : links) {
				if ("enclosure".equals(i.rel)) {
					Enclosure e = new Enclosure();
					try {
						e.setLength(Long.parseLong(i.length));
					}
					catch (NumberFormatException exception) { } // quiet
					e.setType(i.type);
					e.setURL(i.href);
					addEnclosure(e);
				}
				else if ((commentsURL == null) && "replies".equals(i.rel)) {
					commentsURL = i;
				}
				// 1. Use ""alternate" link if available
				else if ((storyURL == null) && "alternate".equals(i.rel)) {
					storyURL = i;
				}
			}

			// 2. No "alternate" link found; use first one
			if (storyURL == null)
				storyURL = links.get(0);
		}
		if (commentsURL != null)
			setCommentsLink(commentsURL.href);
		if (storyURL != null)
			setLink(storyURL.href);

		if (title != null) {
			//if ("html".equals(title.type)) {
				setTitle(title.value);
			//}
/*
			if ("xhtml".equals(title.type)) {
				if (title.xhtmlDiv != null)
					setTitle(title.xhtmlDiv.value);
				else
					setTitle(title.value);
			}
			else { // text/unknown
				setTitle(title.value);
			}
*/
		}
	}
	
	// protected classes
	
	protected static final class Link {

		// protected
		
		@XmlAttribute protected String href;
		@XmlAttribute protected String hreflang;
		@XmlAttribute protected String length;
		@XmlAttribute protected String type;
		@XmlAttribute protected String rel;
		@XmlAttribute protected String title;

	}
	
	// private
	
/*
	private static final class XHTMLDiv {
		
		// private
		
		@XmlValue
		private String value;
		
	}
*/

	private static final class Title {
		
		// private
		
		//@XmlAttribute(name = "type")
		//private String type;

		@XmlValue
		private String value;

/* FIXME: 2.0: Atom: XHTML title
		@XmlElement(name = "div", namespace = "http://www.w3.org/1999/xhtml")
		private XHTMLDiv xhtmlDiv;
*/ 
		
	}

}
