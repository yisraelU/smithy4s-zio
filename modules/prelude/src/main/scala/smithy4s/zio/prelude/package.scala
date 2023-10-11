package smithy4s.zio

import scala.util.hashing.MurmurHash3

package object prelude {

  def combineHash(start: Int, hashes: Int*): Int = {
    var hashResult = start
    hashes.foreach(hash => hashResult = MurmurHash3.mix(hashResult, hash))
    MurmurHash3.finalizeHash(hashResult, hashes.length)
  }

}
