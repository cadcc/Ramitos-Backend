package cl.cadcc.ramitos.utils

import java.nio.charset.StandardCharsets
 
object Base32 {
 
  private val Alphabet: Array[Char] = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray
 
  // Reverse lookup (case-insensitive): ASCII code -> 5-bit value, or -1
  private val Index: Array[Int] = {
    val a = Array.fill(128)(-1)
    Alphabet.zipWithIndex.foreach { case (c, i) =>
      a(c) = i
      a(c.toLower) = i
    }
    a
  }
 
  /** Encode bytes to Base32. `padding = true` appends '=' to a multiple of 8 chars, per RFC 4648. */
  def encode(bytes: Array[Byte], padding: Boolean = false): String = {
    val sb = new StringBuilder((bytes.length + 4) / 5 * 8)
    var buffer = 0 // holds at most 12 pending bits
    var bits = 0
    var i = 0
    while (i < bytes.length) {
      buffer = ((buffer << 8) | (bytes(i) & 0xff)) & 0xfff
      bits += 8
      while (bits >= 5) {
        bits -= 5
        sb.append(Alphabet((buffer >>> bits) & 31))
      }
      i += 1
    }
    if (bits > 0) sb.append(Alphabet((buffer << (5 - bits)) & 31))
    if (padding) while (sb.length % 8 != 0) sb.append('=')
    sb.toString
  }
 
  /** Encode bytes and return the Base32 text as UTF-8 bytes. */
  def encodeToUtf8(bytes: Array[Byte], padding: Boolean = false): Array[Byte] =
    encode(bytes, padding).getBytes(StandardCharsets.UTF_8)
 
  /** Decode Base32 text. Accepts upper or lower case, with or without '=' padding.
    * @throws IllegalArgumentException on characters outside the alphabet
    */
  def decode(s: String): Array[Byte] = {
    var end = s.length
    while (end > 0 && s.charAt(end - 1) == '=') end -= 1
 
    val out = new Array[Byte](end * 5 / 8)
    var buffer = 0 // holds at most 12 pending bits
    var bits = 0
    var o = 0
    var i = 0
    while (i < end) {
      val c = s.charAt(i)
      val d = if (c < 128) Index(c) else -1
      require(d >= 0, s"Invalid Base32 character '$c' at position $i")
      buffer = ((buffer << 5) | d) & 0xfff
      bits += 5
      if (bits >= 8) {
        bits -= 8
        out(o) = ((buffer >>> bits) & 0xff).toByte
        o += 1
      }
      i += 1
    }
    out // any leftover (< 8) bits are the encoder's zero fill and are dropped
  }
 
  /** Decode from UTF-8 encoded Base32 text. */
  def decodeUtf8(utf8: Array[Byte]): Array[Byte] =
    decode(new String(utf8, StandardCharsets.UTF_8))
}
