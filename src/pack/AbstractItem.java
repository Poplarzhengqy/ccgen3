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

package org.makagiga.feeds;

import static org.makagiga.commons.UI._;

import java.util.List;
import javax.xml.bind.annotation.XmlTransient;

import org.makagiga.commons.MArrayList;
import org.makagiga.commons.TK;
import org.makagiga.commons.annotation.Important;
import org.makagiga.feeds.archive.ArchiveItem;

public abstract class AbstractItem {
	
	// private
	
	// non-standard
	private boolean _new;
	private boolean read;

	private List<Enclosure> enclosure;
	private String author;
	private String commentsLink;
	private String description;
	private String id;
	private String link;
	private String longDescription;
	private String publishedDate;
	private String title;
	private String updatedDate;
	
	// public
	
	public AbstractItem() { }

	/**
	 * @since 3.8.8
	 */
	public ArchiveItem.DateFormat getArchiveDateFormat() { return ArchiveItem.DateFormat.UNKNOWN; };
	
	// author

// TODO: 2.2: Atom 1.0
	public String getAuthor() { return author; }
	
	public boolean isAuthorPresent() { return author != null; }
	
	public void setAuthor(final String value) { author = value; }

	// comments

	public String getCommentsLink() { return commentsLink; }
	
	public boolean isCommentsLinkPresent() { return (commentsLink != null); }
	
	public void setCommentsLink(final String value) {
		if (value == null) {
			commentsLink = null;
		}
		else {
			commentsLink = value.trim(); // may be "\n"
			if (commentsLink.isEmpty())
				commentsLink = null;
		}
	}

	// description

	public String getDescription() { return description; }
	
	public boolean isDescriptionPresent() { return description != null; }
	
	public void setDescription(final String value) { description = value; }

	// enclosures

	/**
	 * @since 1.2
	 */
	public void addEnclosure(final Enclosure value) {
		if (enclosure == null)
			enclosure = new MArrayList<>();
		enclosure.add(value);
	}

	/**
	 * @since 1.2
	 */
	public List<Enclosure> getEnclosure() { return enclosure; }

	/**
	 * @since 1.2
	 */
	public boolean hasEnclosure() {
		return !TK.isEmpty(enclosure);
	}

	/**
	 * @since 1.2
	 */
	public void setEnclosure(final List<Enclosure> value) { enclosure = value; }

	// id
	
	/**
	 * This may return a {@code null} ID for some Feed Channels.
	 *
	 * @deprecated As of 4.4, replaced by {@link #getUniqueID()}
	 */
	@Deprecated
	public String getId() { return id; }
	
	public boolean isIdPresent() { return id != null; }
	
	public void setId(final String value) { id = value; }

	// link
	
	public String getLink() { return link; }
	
	public boolean isLinkPresent() { return link != null; }
	
	public void setLink(final String value) { link = value; }

	// long description (optional)
	
	public String getLongDescription() { return longDescription; }
	
	public boolean isLongDescriptionPresent() { return longDescription != null; }
	
	public void setLongDescription(final String value) { longDescription = value; }

	// published date

	public String getPublishedDate() { return publishedDate; }
	
	/**
	 * @since 2.0
	 */
	public java.util.Date getPublishedDateAsDate() { return null; }
	
	public boolean isPublishedDatePresent() { return publishedDate != null; }
	
	public void setPublishedDate(final String value) { publishedDate = value; }

	// title
	
	public String getTitle() { return title; }

	public boolean isTitlePresent() { return title != null; }
	
	public void setTitle(final String value) { title = value; }

	// updated date

	public String getUpdatedDate() { return updatedDate; }

	/**
	 * @since 2.0
	 */
	public java.util.Date getUpdatedDateAsDate() { return null; }
	
	public boolean isUpdatedDatePresent() { return updatedDate != null; }
	
	public void setUpdatedDate(final String value) { updatedDate = value; }

	// non-standard
	
	/**
	 * @since 2.0
	 */
	public java.util.Date getDate() {
		if (isUpdatedDatePresent())
			return getUpdatedDateAsDate();
		
		if (isPublishedDatePresent())
			return getPublishedDateAsDate();

		return null;
	}
	
	/**
	 * @since 2.0
	 */
	@XmlTransient
	public String getText() {
		if (isLongDescriptionPresent())
			return getLongDescription();
		
		if (isDescriptionPresent())
			return getDescription();
		
		return "";
	}
	
	/**
	 * @since 1.2
	 */
	@XmlTransient
	public String getUniqueID() {
		if (isIdPresent())
			return getId();
		
		return getLink();
	}

	@XmlTransient
	public boolean isNew() { return _new; }
	
	public void setNew(final boolean value) { _new = value; }
	
	public boolean isRead() { return read; }
	
	public void setRead(final boolean value) { read = value; }

	@Important
	@Override
	public String toString() {
		String result = isTitlePresent() ? title : null;
		if (TK.isEmpty(result)) {
			// use description
			if (isDescriptionPresent())
				result = getDescription();
		}
		
		return TK.isEmpty(result) ? _("No Title") : result;
	}

	// protected

	protected void onFinish() { }

}
