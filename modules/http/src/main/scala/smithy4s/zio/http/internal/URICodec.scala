package smithy4s.zio.http.internal

object URICodec {

  def encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
  def decode(s: String): String = java.net.URLDecoder.decode(s, "UTF-8")

}
