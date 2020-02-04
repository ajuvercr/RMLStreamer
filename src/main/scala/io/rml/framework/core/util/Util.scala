package io.rml.framework.core.util

import java.io.{ByteArrayInputStream, File, FileInputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.regex.Pattern

import io.rml.framework.core.extractors.MappingReader
import io.rml.framework.core.model.{FormattedRMLMapping, Literal}
import io.rml.framework.shared.ReadException
import org.apache.commons.validator.routines.UrlValidator

import scala.collection.mutable.ListBuffer
import scala.io.Source


object Util {

  // Without support for custom registered languages of length 5-8 of the IANA language-subtag-registry
  private val regexPatternLanguageTag = Pattern.compile("^((?:(en-GB-oed|i-ami|i-bnn|i-default|i-enochian|i-hak|i-klingon|i-lux|i-mingo|i-navajo|i-pwn|i-tao|i-tay|i-tsu|sgn-BE-FR|sgn-BE-NL|sgn-CH-DE)|(art-lojban|cel-gaulish|no-bok|no-nyn|zh-guoyu|zh-hakka|zh-min|zh-min-nan|zh-xiang))|((?:([A-Za-z]{2,3}(-(?:[A-Za-z]{3}(-[A-Za-z]{3}){0,2}))?)|[A-Za-z]{4})(-(?:[A-Za-z]{4}))?(-(?:[A-Za-z]{2}|[0-9]{3}))?(-(?:[A-Za-z0-9]{5,8}|[0-9][A-Za-z0-9]{3}))*(-(?:[0-9A-WY-Za-wy-z](-[A-Za-z0-9]{2,8})+))*(-(?:x(-[A-Za-z0-9]{1,8})+))?)|(?:x(-[A-Za-z0-9]{1,8})+))$")

  private val baseDirectiveCapture = "@base <([^<>]*)>.*".r
  private val VALIDATOR = new UrlValidator()


  /**
    * Check if conforming to https://tools.ietf.org/html/bcp47#section-2.2.9
    *
    * @param s language tag
    * @return True if valid language tag according to BCP 47
    */
  def isValidrrLanguage(s: String): Boolean = regexPatternLanguageTag.matcher(s).matches


  def getFileInputStream(f: File): InputStream = new FileInputStream(f)

  def getFileInputStream(s: String): InputStream = Files.newInputStream(Paths.get(s))


  /**
    * Extract base url from input stream of turtle mapping file
    *
    * @param input inputstream of turtle mapping file
    * @return base url string
    */
  def getBaseDirective(input: InputStream): String = {
    val lineIter = Source.fromInputStream(input).getLines()

    var directives = ListBuffer[String]()
    // breaks aren't advisable to use in scala so we have to do it the hard way with while
    var done = false
    while (lineIter.hasNext && !done) {
      val line = lineIter.next().trim
      if (line.length > 0) {
        if (line.head != '@') {
          done = true
        } else if (line.contains("@base")) {

          directives += line
        }
      }
    }

    if (directives.nonEmpty) {
      getBaseDirective(directives.toList)
    } else {
      ""
    }
  }

  def getBaseDirective(turtleDump: String): String = {
    val stream = new ByteArrayInputStream(turtleDump.getBytes(StandardCharsets.UTF_8))
    getBaseDirective(stream)
  }

  /**
    * Extract the base directive from a list of directives in the
    * turtle file
    *
    * @param directives list of string containing directives of a turtle file
    * @return base directive string defined in the list of directives
    */
  def getBaseDirective(directives: List[String]): String = {
    val filtered = directives.map(regexCaptureBaseUrl).filterNot(_.isEmpty)
    if (filtered.nonEmpty) {
      filtered.head
    } else {
      ""
    }
  }


  /**
    *
    * Extract base url from the base directive string using regex
    *
    * @param directive a base directive string
    * @return base url string
    */
  def regexCaptureBaseUrl(directive: String): String = {
    directive match {
      case baseDirectiveCapture(url) => url
      case _ => ""
    }
  }


  def isValidUri(uri: String): Boolean = {
    VALIDATOR.isValid(uri)
  }



  def isRootIteratorTag(tag:Option[Literal]): Boolean = {
    tag match {
      case None => true
      case Some(x) =>  io.rml.framework.flink.source.Source.DEFAULT_ITERATOR_SET.contains(x.toString)
    }
  }

  // auto-close resources, seems to be missing in Scala
  def tryWith[R, T <: AutoCloseable](resource: T)(doWork: T => R): R = {
    try {
      doWork(resource)
    }
    finally {
      try {
        if (resource != null) {
          resource.close()
        }
      }
      catch {
        case e: Exception => {
          throw new ReadException(e.getMessage)
        }
      }
    }
  }

  def guessFormatFromFileName(fileName: String): Option[Format] = {
    val suffix = fileName.substring(fileName.lastIndexOf('.')).toLowerCase
    suffix match {
      case ".ttl" => Some(Turtle)
      case ".nt" => Some(NTriples)
      case ".nq" => Some(NQuads)
      case ".json" => Some(JSON_LD)
      case ".json-ld" => Some(JSON_LD)
      case _ => None
    }
  }

  /**
    * Utility method for reading a mapping file and converting it to a formatted RML mapping.
    *
    * @param path
    * @return
    */
  def readMappingFile(path: String): FormattedRMLMapping = {
    val classLoader = getClass.getClassLoader
    val file_1 = new File(path)
    val mapping = if (file_1.isAbsolute) {
      val file = new File(path)
      MappingReader().read(file)
    } else {
      val file = new File(classLoader.getResource(path).getFile)
      MappingReader().read(file)
    }

    FormattedRMLMapping.fromRMLMapping(mapping)
  }

}
