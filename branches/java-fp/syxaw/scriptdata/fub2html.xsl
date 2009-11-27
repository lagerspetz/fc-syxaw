<!--
    fub2html - a stylesheet for converting FubML into HTML

This stylesheet makes an HTML page out of a FubML bibliography.  By default
it outputs all entries given as input.  The parameter 'project' can be used
to restrict the output to those entries having an extension with the name
project and value the value given for the parameter.  In this case the
'visibility' parameter can be used to further restrict the entries to only
those having a certain visibility.  The parameter 'category' has as its
value a list of strings separated by '+' signs, and if it is given, entries
having one of these as an extension named category will be output, grouped
by category.  In all cases the output is from the newest to the oldest (in
the category case each category will have this order).
-->
<!-- Version hacked for 29.9 demo by ctl (removed http://exslt.org/common
 extensions-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
		xmlns:fub="http://www.hiit.fi/fuego/fc/fub"
		xmlns:ref="http://www.hiit.fi/fc/xml/ref"
		xmlns:exsl="http://exslt.org/common"
		version="1.0"
		exclude-result-prefixes="fub exsl">
  <xsl:param name="project"/>
  <xsl:param name="visibility"/>
  <xsl:param name="category"/>
  <xsl:param name="owner"/>
  <xsl:param name="bibfile"/>
  <xsl:param name="absfile"/>
  <xsl:param name="visible-abstracts"/>

  <xsl:output method="html" doctype-public="-//W3C//DTD HTML 4.01//EN"
	      indent="yes"/>

  <fub:months>
    <month order="1">January</month>
    <month order="2">February</month>
    <month order="3">March</month>
    <month order="4">April</month>
    <month order="5">May</month>
    <month order="6">June</month>
    <month order="7">July</month>
    <month order="8">August</month>
    <month order="9">September</month>
    <month order="10">October</month>
    <month order="11">November</month>
    <month order="12">December</month>
  </fub:months>
  <xsl:key name="journal" match="entry[@type = 'journal']"
	   use="@key"/>
  <xsl:key name="book" match="entry[@type = 'book']" use="@key"/>
  <xsl:key name="conference" match="entry[@type = 'proceedings']"
	   use="@key"/>
  <xsl:key name="entry" match="entry" use="@key"/>
  <xsl:key name="person" match="person" use="@key"/>
  <xsl:key name="entity" match="entity" use="@key"/>

  <xsl:variable name="catsep" select="'+'"/>

  <xsl:template match="/bibliography|/ref:node"> <!--latter rule for ref root -->
    <xsl:variable name="title">
      <xsl:choose>
	<xsl:when test="$project">
	  <xsl:value-of select="normalize-space($project)"/>
	  <xsl:text> </xsl:text>
	</xsl:when>
	<xsl:when test="$owner">
	  <xsl:value-of select="normalize-space($owner)"/>
	  <xsl:text>'s </xsl:text>
	</xsl:when>
      </xsl:choose>
      <xsl:text>Bibliography</xsl:text>
    </xsl:variable>
    <html>
      <head>
	<title>
	  <xsl:value-of select="$title"/>
	</title>
	<xsl:choose>
	  <xsl:when test="$visible-abstracts">
	    <link href="bib-abs.css" title="Default" type="text/css"
		  rel="stylesheet"/>
	  </xsl:when>
	  <xsl:otherwise>
	    <link href="bib.css" title="Default" type="text/css"
		  rel="stylesheet"/>
	    <link href="bib-abs.css" title="Visible Abstracts" type="text/css"
		  rel="alternate stylesheet"/>
	  </xsl:otherwise>
	</xsl:choose>
      </head>
      <body>
	<h1><xsl:value-of select="$title"/></h1>
	<xsl:if test="$bibfile">
	  <p class="bibtex-note">
	    The list below is also available in <a
	    href="{$bibfile}">BibTeX format</a>.
	  </p>
	</xsl:if>
	<xsl:if test="not(boolean($visible-abstracts))">
	  <p>
	    If the abstracts of the publications below do not appear
	    when selecting the "Abstract" link, you can select the
	    "Visible Abstracts" style from your browser menu<xsl:if
	    test="$absfile"> or go to the <a
	    href="{$absfile}">alternate page</a> that shows all
	    abstracts </xsl:if>.
	  </p>
	</xsl:if>
	<xsl:choose>
	  <xsl:when test="$category">
	    <xsl:call-template name="process-categories">
	      <xsl:with-param name="category" select="$category"/>
	    </xsl:call-template>
	  </xsl:when>
	  <xsl:otherwise>
	    <ul>
	      <xsl:choose>
		<xsl:when test="$project">
		  <xsl:for-each select="entry[extension[@name='project'
					and name=$project]]">
		    <xsl:sort order="descending" data-type="number"
			      select="year | key('book', @book)/year
				      | key('conference', @conference)/year"/>
		    <xsl:sort order="descending" data-type="number"
			      select="month | key('book', @book)/month
				      | key('conference', @conference)/month"/>
		    <xsl:if test="not($visibility) or
				  extension/visibility=$visibility">
		      <xsl:apply-templates select="."/>
		    </xsl:if>
		  </xsl:for-each>
		</xsl:when>
		<xsl:otherwise>
		  <xsl:for-each select="entry|*">
		    <xsl:sort order="descending" data-type="number"
			      select="year | key('book', @book)/year
				      | key('conference', @conference)/year"/>
		    <xsl:sort order="descending" data-type="number"
			      select="month | key('book', @book)/month
				      | key('conference', @conference)/month"/>
		    <xsl:apply-templates select="."/>
		  </xsl:for-each>
		</xsl:otherwise>
	      </xsl:choose>
	    </ul>
	  </xsl:otherwise>
	</xsl:choose>
	<hr/>
	<p class="creation-note">
	  This page generated by <a
	  href="http://www.iki.fi/ashar/software/feather.html">Feather</a>,
	  a toolkit for the FubML bibliography format.
	</p>
      </body>
    </html>
  </xsl:template>

  <xsl:template name="ltrim">
    <xsl:param name="s"/>
    <xsl:value-of select="concat(substring(translate($s,' &#9;&#10;&#13;',''),
			  1,1),substring-after($s,substring(translate($s,
			  ' &#9;&#10;&#13;',''),1,1)))"/>
  </xsl:template>

  <xsl:template name="rtrim">
    <xsl:param name="s"/>
    <xsl:param name="i" select="string-length($s)"/>
    <xsl:choose>
      <xsl:when test="translate(substring($s,$i,1),' &#9;&#10;&#13;','')">
        <xsl:value-of select="substring($s,1,$i)"/>
      </xsl:when>
      <xsl:when test="$i&lt;2"/>
      <xsl:otherwise>
        <xsl:call-template name="rtrim">
          <xsl:with-param name="s" select="$s"/>
          <xsl:with-param name="i" select="$i - 1"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="process-categories">
    <xsl:param name="category"/>
    <xsl:choose>
      <xsl:when test="contains($category, $catsep)">
	<xsl:call-template name="process-category">
	  <xsl:with-param name="category"
			  select="substring-before($category, $catsep)"/>
	</xsl:call-template>
	<xsl:call-template name="process-categories">
	  <xsl:with-param name="category"
			  select="substring-after($category, $catsep)"/>
	</xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
	<xsl:call-template name="process-category">
	  <xsl:with-param name="category" select="$category"/>
	</xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="process-category">
    <xsl:param name="category"/>
    <h3>
      <xsl:value-of select="$category"/>
    </h3>
    <ul>
      <xsl:choose>
	<xsl:when test="$project">
	  <xsl:for-each select="entry[extension[@name='project'
				and name=$project] and
				extension[@name='category'
				and name=$category]]">
	    <xsl:sort order="descending" data-type="number"
		      select="year | key('book', @book)/year
			      | key('conference', @conference)/year"/>
	    <xsl:sort order="descending" data-type="number"
		      select="month | key('book', @book)/month
			      | key('conference', @conference)/month"/>
	    <xsl:if test="not($visibility) or
			  extension/visibility=$visibility">
	      <xsl:apply-templates select="."/>
	    </xsl:if>
	  </xsl:for-each>
	</xsl:when>
	<xsl:otherwise>
	  <xsl:for-each select="entry[extension[@name='category'
				and name=$category]]">
	    <xsl:sort order="descending" data-type="number"
		      select="year | key('book', @book)/year
			      | key('conference', @conference)/year"/>
	    <xsl:sort order="descending" data-type="number"
		      select="month | key('book', @book)/month
			      | key('conference', @conference)/month"/>
	    <xsl:apply-templates select="."/>
	  </xsl:for-each>
	</xsl:otherwise>
      </xsl:choose>
    </ul>
  </xsl:template>

  <xsl:template match="entry[@type='journal' or @type='proceedings']"/>

  <xsl:template name="output-person">
    <xsl:param name="person"/>
    <xsl:choose>
      <xsl:when test="$person/first">
	<xsl:value-of select="normalize-space($person/first)"/>
	<xsl:text> </xsl:text>
	<xsl:if test="$person/middle">
	  <xsl:value-of select="normalize-space($person/middle)"/>
	  <xsl:text>. </xsl:text>
	</xsl:if>
	<xsl:value-of select="normalize-space($person/last)"/>
      </xsl:when>
      <xsl:otherwise>
	<xsl:value-of select="normalize-space($person)"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="entry">
    <li id="{@key}">
      <xsl:for-each select="author">
	<xsl:if test="last() > 1">
	  <xsl:choose>
	    <xsl:when test="position() = last() and not(parent::*/etal)">
	      <xsl:if test="last() > 2">
		<xsl:text>,</xsl:text>
	      </xsl:if>
	      <xsl:text> and </xsl:text>
	    </xsl:when>
	    <xsl:when test="position() != 1">
	      <xsl:text>, </xsl:text>
	    </xsl:when>
	  </xsl:choose>
	</xsl:if>
	<span class="person">
	  <xsl:choose>
	    <xsl:when test="@ref">
	      <xsl:choose>
		<xsl:when test="key('person', @ref)/url">
		  <a href="{normalize-space(key('person', @ref)/url)}">
		    <xsl:call-template name="output-person">
		      <xsl:with-param name="person"
				      select="key('person', @ref)/name"/>
		    </xsl:call-template>
		  </a>
		</xsl:when>
		<xsl:when test="not(key('person', @ref))">
		   <span class="pref"><xsl:value-of select="@ref"/></span>
		</xsl:when>
		<xsl:otherwise>
		  <xsl:call-template name="output-person">
		    <xsl:with-param name="person"
				    select="key('person', @ref)/name"/>
		  </xsl:call-template>
		</xsl:otherwise>
	      </xsl:choose>
	    </xsl:when>
	    <xsl:otherwise>
	      <xsl:call-template name="output-person">
		<xsl:with-param name="person" select="."/>
	      </xsl:call-template>
	    </xsl:otherwise>
	  </xsl:choose>
	</span>
      </xsl:for-each>
      <xsl:if test="etal">
	<xsl:text> et al</xsl:text>
      </xsl:if>
      <xsl:if test="author">
	<xsl:text>. </xsl:text>
      </xsl:if>
      <span class="title">
	<xsl:choose>
	  <xsl:when test="url">
	    <a href="{normalize-space(url)}">
	      <xsl:apply-templates select="title"/>
	    </a>
	  </xsl:when>
	  <xsl:otherwise>
	    <xsl:apply-templates select="title"/>
	  </xsl:otherwise>
	</xsl:choose>
      </span>
      <xsl:apply-templates select="." mode="addt"/>
      <xsl:variable name="year" select="year | key('book', @book)/year
					| key('conference',
					@conference)/year
					| journal/year"/>
      <xsl:if test="$year">
	<xsl:text>, </xsl:text>
	<xsl:variable name="month" select="month
					   | key('book', @book)/month
					   | key('conference',
					   @conference)/month
					   | journal/month"/>
	<xsl:if test="$month">
	  <xsl:value-of select="document('')/*/fub:months/month[@order=$month]"/>
	  <xsl:text> </xsl:text>
	</xsl:if>
	<xsl:value-of select="normalize-space($year)"/>
      </xsl:if>
      <xsl:if test="note">
	<xsl:text>, </xsl:text>
	<span class="note">
	  <xsl:value-of select="normalize-space(note)"/>
	</span>
      </xsl:if>
      <xsl:if test="abstract">
	<div class="abstract">
	  <a class="abshead" href="#abs:{@key}">Abstract</a>
	  <div class="abstext">
	    <xsl:apply-templates select="abstract/*"/>
	  </div>
	</div>
      </xsl:if>
      <xsl:for-each select="comment">
	<p class="comment">
	  <span class="commenter">
	    <xsl:value-of select="@source"/>
	  </span>
	  <xsl:text>: </xsl:text>
	  <xsl:apply-templates/>
	</p>
      </xsl:for-each>
      <xsl:if test="references">
	<div class="citelist">
	  <p>
	    References
	  </p>
	  <ul>
	    <xsl:for-each select="references/cite">
	      <li>
		<a href="#{.}" class="cite">
		  <xsl:apply-templates select="key('entry', .)/title"/>
		</a>
	      </li>
	    </xsl:for-each>
	  </ul>
	</div>
      </xsl:if>
    </li>
  </xsl:template>

  <xsl:template match="entry[@type='mastersthesis']" mode="addt">
    <xsl:text>, </xsl:text>
    <xsl:choose>
      <xsl:when test="type">
	<xsl:value-of select="normalize-space(type)"/>
      </xsl:when>
      <xsl:otherwise>
	<xsl:text>Master's thesis</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:text>, </xsl:text>
    <span class="school">
      <xsl:choose>
	<xsl:when test="school/@ref">
	  <xsl:choose>
            <xsl:when test="not(key('entity', school/@ref))">
            <span class="iref"><xsl:value-of select="school/@ref" /></span> 
	    </xsl:when>
	    <xsl:when test="key('entity', school/@ref)/url">
	      <a href="{key('entity', school/@ref)/url}">
		<xsl:value-of select="normalize-space(key('entity',
				      school/@ref)/title)"/>
	      </a>
	    </xsl:when>
	    <xsl:otherwise>
	      <xsl:value-of select="normalize-space(key('entity',
				    school/@ref)/title)"/>
	    </xsl:otherwise>
	  </xsl:choose>
	</xsl:when>
	<xsl:otherwise>
	  <xsl:value-of select="normalize-space(school)"/>
	</xsl:otherwise>
      </xsl:choose>
    </span>
  </xsl:template>

  <xsl:template match="entry[@type='phdthesis']" mode="addt">
    <xsl:text>, </xsl:text>
    <xsl:choose>
      <xsl:when test="type">
	<xsl:value-of select="normalize-space(type)"/>
      </xsl:when>
      <xsl:otherwise>
	<xsl:text>PhD thesis</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:text>, </xsl:text>
    <span class="school">
      <xsl:choose>
	<xsl:when test="school/@ref">
	  <xsl:choose>
	    <xsl:when test="key('entity', school/@ref)/url">
	      <a href="{key('entity', school/@ref)/url}">
		<xsl:value-of select="normalize-space(key('entity',
				      school/@ref)/title)"/>
	      </a>
	    </xsl:when>
	    <xsl:otherwise>
	      <xsl:value-of select="normalize-space(key('entity',
				    school/@ref)/title)"/>
	    </xsl:otherwise>
	  </xsl:choose>
	</xsl:when>
	<xsl:otherwise>
	  <xsl:value-of select="normalize-space(school)"/>
	</xsl:otherwise>
      </xsl:choose>
    </span>
  </xsl:template>

  <xsl:template match="entry[@book]" mode="addt">
    <xsl:text>. In </xsl:text>
    <xsl:for-each select="key('book', @book)">
      <span class="booktitle">
	<xsl:choose>
	  <xsl:when test="url">
	    <a href="{normalize-space(url)}">
	      <xsl:apply-templates select="title"/>
	    </a>
	  </xsl:when>
	  <xsl:otherwise>
	    <xsl:apply-templates select="title"/>
	  </xsl:otherwise>
	</xsl:choose>
      </span>
      <xsl:if test="edition">
	<span class="edition">
	  <xsl:text>, </xsl:text>
	  <xsl:value-of select="normalize-space(edition)"/>
	  <xsl:text> edition</xsl:text>
	</span>
      </xsl:if>
      <xsl:apply-templates select="series"/>
      <xsl:apply-templates select="volume|number"/>
      <xsl:apply-templates select="publisher"/>
    </xsl:for-each>
  </xsl:template>

  <xsl:template match="entry[@conference]" mode="addt">
    <xsl:text>. In </xsl:text>
    <xsl:choose>
    <xsl:when test="key('conference', @conference)">	
    <xsl:for-each select="key('conference', @conference)">
      <span class="conftitle">
	<xsl:choose>
	  <xsl:when test="url">
	    <a href="{normalize-space(url)}">
	      <xsl:apply-templates select="title"/>
	    </a>
	  </xsl:when>
	  <xsl:otherwise>
	    <xsl:apply-templates select="title"/>
	  </xsl:otherwise>
	</xsl:choose>
      </span>
      <xsl:apply-templates select="series"/>
      <xsl:apply-templates select="volume|number"/>
      <xsl:apply-templates select="publisher"/>
    </xsl:for-each>
    </xsl:when>
    <xsl:otherwise><span class="pref">
	<xsl:value-of select="@conference"/></span>
    </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="entry[@type='techreport']" mode="addt">
    <xsl:text>, </xsl:text>
    <xsl:choose>
      <xsl:when test="type">
	<xsl:value-of select="normalize-space(type)"/>
      </xsl:when>
      <xsl:otherwise>
	<xsl:text>Technical Report</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:if test="number">
      <xsl:text> </xsl:text>
      <span class="trnumber">
	<xsl:value-of select="normalize-space(number)"/>
      </span>
    </xsl:if>
    <xsl:text>, </xsl:text>
    <span class="institution">
      <xsl:choose>
	<xsl:when test="institution/@ref">
	  <xsl:choose>
	    <xsl:when test="key('entity', institution/@ref)/url">
	      <a href="{key('entity', institution/@ref)/url}">
		<xsl:value-of select="normalize-space(key('entity',
				      institution/@ref)/title)"/>
	      </a>
	    </xsl:when>
	    <xsl:otherwise>
	      <xsl:value-of select="normalize-space(key('entity',
				    institution/@ref)/title)"/>
	    </xsl:otherwise>
	  </xsl:choose>
	</xsl:when>
	<xsl:otherwise>
	  <xsl:value-of select="normalize-space(institution)"/>
	</xsl:otherwise>
      </xsl:choose>
    </span>
  </xsl:template>

  <xsl:template match="entry[journal]" mode="addt">
    <xsl:text>. </xsl:text>
    <xsl:for-each select="journal">
      <span class="jtitle">
	<xsl:apply-templates select="key('journal', @ref)/title"/>
      </span>
      <xsl:text> </xsl:text>
      <span class="jvolume">
	<xsl:value-of select="normalize-space(volume)"/>
      </span>
      <xsl:if test="number">
	<xsl:text> (</xsl:text>
	<span class="jnumber">
	  <xsl:value-of select="normalize-space(number)"/>
	</span>
	<xsl:text>)</xsl:text>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>

  <xsl:template match="entry" mode="addt"/>

  <xsl:template match="publisher">
    <xsl:text>, </xsl:text>
    <span class="publisher">
      <xsl:choose>
	<xsl:when test="@ref">
	  <xsl:choose>
	    <xsl:when test="key('entity', @ref)/url">
	      <a href="{key('entity', @ref)/url}">
		<xsl:value-of select="normalize-space(key('entity', @ref)
				      /title)"/>
	      </a>
	    </xsl:when>
	    <xsl:otherwise>
	      <xsl:value-of select="normalize-space(key('entity', @ref)
				    /title)"/>
	    </xsl:otherwise>
	  </xsl:choose>
	</xsl:when>
	<xsl:otherwise>
	  <xsl:value-of select="normalize-space(.)"/>
	</xsl:otherwise>
      </xsl:choose>
    </span>
  </xsl:template>

  <xsl:template match="series">
    <xsl:text>, </xsl:text>
    <span class="bookseries">
      <xsl:value-of select="normalize-space(.)"/>
    </span>
  </xsl:template>

  <xsl:template match="volume|number">
    <xsl:text> </xsl:text>
    <span class="bookvolume">
      <xsl:value-of select="normalize-space(.)"/>
    </span>
  </xsl:template>

  <xsl:template match="title">
    <xsl:variable name="etitle">
      <xsl:apply-templates/>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="count(*|text()) &lt;= 1">
	<xsl:value-of select="normalize-space(text())"/>
      </xsl:when>
      <xsl:otherwise>
	<xsl:for-each select="*|text()">
	  <xsl:choose>
	    <xsl:when test="position()=1">
	      <xsl:call-template name="ltrim">
		<xsl:with-param name="s" select="."/>
	      </xsl:call-template>
	    </xsl:when>
	    <xsl:when test="position()=last()">
	      <xsl:call-template name="rtrim">
		<xsl:with-param name="s" select="."/>
		<xsl:with-param name="i" select="string-length(.)"/>
	      </xsl:call-template>
	    </xsl:when>
	    <xsl:otherwise>
	      <xsl:apply-templates select="."/>
	    </xsl:otherwise>
	  </xsl:choose>
	</xsl:for-each>
      </xsl:otherwise>
    </xsl:choose>
    <!--<xsl:call-template name="post-title">
      <xsl:with-param name="nodes" select="exsl:node-set($etitle)"/>
    </xsl:call-template> -->
  </xsl:template>

  <xsl:template name="post-title">
    <xsl:param name="nodes"/>
    <xsl:choose>
      <xsl:when test="count($nodes/*|$nodes/text()) &lt;= 1">
	<xsl:value-of select="normalize-space($nodes/text())"/>
      </xsl:when>
      <xsl:otherwise>
	<xsl:for-each select="$nodes/*|$nodes/text()">
	  <xsl:choose>
	    <xsl:when test="position()=1">
	      <xsl:call-template name="ltrim">
		<xsl:with-param name="s" select="."/>
	      </xsl:call-template>
	    </xsl:when>
	    <xsl:when test="position()=last()">
	      <xsl:call-template name="rtrim">
		<xsl:with-param name="s" select="."/>
		<xsl:with-param name="i" select="string-length(.)"/>
	      </xsl:call-template>
	    </xsl:when>
	    <xsl:otherwise>
	      <xsl:copy-of select="."/>
	    </xsl:otherwise>
	  </xsl:choose>
	</xsl:for-each>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="pre">
    <xsl:value-of select="normalize-space(.)"/>
  </xsl:template>

  <xsl:template match="special">
    <xsl:choose>
      <xsl:when test="html">
	<xsl:apply-templates select="html/*|html/text()"/>
      </xsl:when>
      <xsl:otherwise>
	<xsl:value-of select="default"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

 <xsl:template match="ref:tree" priority="10">
 	<li><span class="reftree"><xsl:value-of select="@id"/></span></li>
 </xsl:template>

  <xsl:template match="*">
    <xsl:element name="{name()}">
      <xsl:for-each select="@*">
	<xsl:attribute name="{name()}">
	  <xsl:value-of select="."/>
	</xsl:attribute>
      </xsl:for-each>
      <xsl:apply-templates select="*|text()"/>
    </xsl:element>
  </xsl:template>

</xsl:stylesheet>

<!-- arch-tag: cc4aab8e-8375-4fbb-8e8b-220e3e62f927
-->
