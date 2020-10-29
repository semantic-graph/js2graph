package edu.washington.cs.js2graph

import scala.util.Try

object NumCruncher {

  def isShort(aString: String): Boolean = Try(aString.toLong).isSuccess
  def isInt(aString: String): Boolean = Try(aString.toInt).isSuccess
  def isLong(aString: String): Boolean = Try(aString.toLong).isSuccess
  def isDouble(aString: String): Boolean = Try(aString.toDouble).isSuccess
  def isFloat(aString: String): Boolean = Try(aString.toFloat).isSuccess

  /** @param x the string to check
    * @return true if the parameter passed is a Java primitive number
    */
  def isNumber(x: String): Boolean = {
    List(isShort(x), isInt(x), isLong(x), isDouble(x), isFloat(x))
      .foldLeft(false)(_ || _)
  }

}
