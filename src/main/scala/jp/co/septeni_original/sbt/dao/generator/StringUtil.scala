package jp.co.septeni_original.sbt.dao.generator

import java.util.StringTokenizer

private[generator] object StringUtil {

  // https://github.com/seasarorg/s2util/blob/3c69319d4260425518487b8781b11d69bcb1908c/s2util/src/main/java/org/seasar/util/lang/StringUtil.java#L107-L117
  private[this] def split(str: String, delm: String): Array[String] = {
    if (str == null || str.length == 0) {
      Array.empty[String]
    } else {
      val buf = Array.newBuilder[String]
      val st = new StringTokenizer(str, delm)
      while (st.hasMoreElements()) {
        buf += st.nextElement().toString()
      }
      buf.result()
    }
  }

  // https://github.com/seasarorg/s2util/blob/3c69319d4260425518487b8781b11d69bcb1908c/s2util/src/main/java/org/seasar/util/lang/StringUtil.java#L278-L285
  def capitalize(name: String): String = {
    if (name == null || name.length == 0) {
      name
    } else {
      val chars = name.toCharArray()
      chars(0) = Character.toUpperCase(chars(0))
      new String(chars)
    }
  }

  // https://github.com/seasarorg/s2util/blob/3c69319d4260425518487b8781b11d69bcb1908c/s2util/src/main/java/org/seasar/util/lang/StringUtil.java#L539-L553
  def camelize(s: String): String = {
    if (s == null) {
      null
    } else {
      val array = split(s.toLowerCase, "_")
      if (array.length == 1) {
        capitalize(s)
      } else {
        val buf = new java.lang.StringBuilder(40)
        array.foreach { c =>
          buf.append(capitalize(c))
        }
        buf.toString()
      }
    }
  }

  // https://github.com/seasarorg/s2util/blob/3c69319d4260425518487b8781b11d69bcb1908c/s2util/src/main/java/org/seasar/util/lang/StringUtil.java#L250-L261
  def decapitalize(name: String): String = {
    if (name == null || name.length == 0) {
      name
    } else {
      val chars = name.toCharArray()
      if (chars.length >= 2 && Character.isUpperCase(chars(0)) && Character.isUpperCase(chars(1))) {
        name
      } else {
        chars(0) = Character.toLowerCase(chars(0))
        new String(chars)
      }
    }
  }

  // https://github.com/seasarorg/s2util/blob/3c69319d4260425518487b8781b11d69bcb1908c/s2util/src/main/java/org/seasar/util/lang/StringUtil.java#L569-L592
  def decamelize(s: String): String = {
    if (s == null) {
      null
    } else if (s.length == 1) {
      s.toUpperCase()
    } else {
      val buf = new StringBuilder(40)
      var pos = 0
      var i = 1
      while (i < s.length) {
        if (Character.isUpperCase(s.charAt(i))) {
          if (buf.length != 0) {
            buf.append('_')
          }
          buf.append(s.substring(pos, i).toUpperCase())
          pos = i
        }
        i += 1
      }
      if (buf.length != 0) {
        buf.append('_')
      }
      buf.append(s.substring(pos, s.length()).toUpperCase())
      buf.toString()
    }
  }
}
