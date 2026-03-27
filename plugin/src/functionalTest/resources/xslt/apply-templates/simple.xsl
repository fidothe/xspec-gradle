<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="#all" version="3.0">

    <xsl:mode on-no-match="shallow-copy" on-multiple-match="fail" />

    <xsl:template match="input">
        <output/>
    </xsl:template>
</xsl:stylesheet>