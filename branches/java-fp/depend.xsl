<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
		version="1.0">
  <xsl:output method="xml" indent="yes"/>

  <xsl:template match="/group">
    <project name="depend">
      <target name="fuego-core">
	<xsl:attribute name="depends"><!--
--><xsl:value-of select="//subproject[1]/@name"/><!--
        --><xsl:for-each select="//subproject[position()>1]/@name">,<xsl:value-of select="."/></xsl:for-each></xsl:attribute>
      </target> 
      <xsl:apply-templates select="//subproject"/>
    </project>
  </xsl:template>

  <xsl:template match="subproject">
    <target name="{@name}-deps">
      <xsl:attribute name="depends">
      <xsl:apply-templates select="depend" mode="attr"/>
      </xsl:attribute>
      <xsl:choose>
	<xsl:when test="@dir">
	  <xsl:apply-templates select="depend" mode="copy">
	    <xsl:with-param name="name" select="@dir"/>
	  </xsl:apply-templates>
	</xsl:when>
	<xsl:otherwise>
	  <xsl:apply-templates select="depend" mode="copy">
	    <xsl:with-param name="name" select="@name"/>
	  </xsl:apply-templates>
	</xsl:otherwise>
      </xsl:choose>
    </target>

    <target name="{@name}">
      <xsl:attribute name="depends">
	<xsl:apply-templates select="depend" mode="attr"/>
      </xsl:attribute>      
      <xsl:choose>
	<xsl:when test="@dir">
	  <xsl:apply-templates select="depend" mode="copy">
	    <xsl:with-param name="name" select="@dir"/>
	  </xsl:apply-templates>
	  <subant target="{@target}">
	    <fileset dir="{@dir}" includes="build.xml"/>
	  <property name="taskdefs.xml" value="ALREADYINCLUDED.taskdefs.xml"/>
	  </subant>
	</xsl:when>
	<xsl:otherwise>
	  <xsl:apply-templates select="depend" mode="copy">
	    <xsl:with-param name="name" select="@name"/>
	  </xsl:apply-templates>
	  <subant target="compile">
	    <fileset dir="{@name}" includes="build.xml"/>
	  <property name="taskdefs.xml" value="ALREADYINCLUDED.taskdefs.xml"/>
	  </subant>
	</xsl:otherwise>
      </xsl:choose>
    </target>

  </xsl:template>

  <xsl:template match="depend" mode="copy">
    <xsl:param name="name"/>
    <xsl:apply-templates select="." mode="copy2">
      <xsl:with-param name="dest" select="$name"/>
      <xsl:with-param name="name" select="text()"/>
    </xsl:apply-templates>
  </xsl:template>

  <xsl:template match="depend" mode="copy2">
    <xsl:param name="name"/>
    <xsl:param name="dest"/>
    <xsl:apply-templates select="//subproject[@name=$name]" mode="copy">
      <xsl:with-param name="dest" select="$dest"/>
    </xsl:apply-templates>
  </xsl:template>

  <xsl:template match="subproject" mode="copy">
    <xsl:param name="dest"/>
    <xsl:choose>
      <xsl:when test="@dir">
	<copy todir="{$dest}/contrib">
	  <fileset dir="{@dir}/${{fp.build.lib}}" includes="*.jar"/> 
	</copy>
      </xsl:when>
      <xsl:otherwise>
	<copy todir="{$dest}/contrib">
	  <fileset dir="{@name}/${{fp.build.lib}}" includes="*.jar"/> 
	</copy>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>


  <xsl:template match="depend[position() > 1]" mode="attr">
    <xsl:text>,</xsl:text>
    <xsl:value-of select="text()"/>
  </xsl:template>

</xsl:stylesheet>

<!-- arch-tag: 2cb58718-6a38-4077-b85e-a3489e800f2e
-->
