package smithy4s.zio.prelude

package object instances {

  private[prelude] object all
      extends EqualsInstances
      with HashInstances
      with DebugInstances

}
