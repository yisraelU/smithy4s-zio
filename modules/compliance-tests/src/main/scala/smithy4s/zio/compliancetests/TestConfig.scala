
package smithy4s.zio.compliancetests

import smithy4s.zio.compliancetests.TestConfig.TestType
import smithy4s.{Enumeration, Hints, Schema, ShapeId}
import smithy.test.AppliesTo

case class TestConfig(
    appliesTo: AppliesTo,
    testType: TestType
) {
  def show: String = s"(${appliesTo.name.toLowerCase}|$testType)"
}

object TestConfig {

  val clientReq = TestConfig(AppliesTo.CLIENT, TestType.Request)
  val clientRes = TestConfig(AppliesTo.CLIENT, TestType.Response)
  val serverReq = TestConfig(AppliesTo.SERVER, TestType.Request)
  val serverRes = TestConfig(AppliesTo.SERVER, TestType.Response)
  sealed abstract class TestType(
      val value: String,
      val intValue: Int
  ) extends Enumeration.Value {
    type EnumType = TestType
    def name: String = value
    def hints: Hints = Hints.empty
    def enumeration: Enumeration[EnumType] = TestType
  }
  object TestType extends smithy4s.Enumeration[TestType] {

    def id: ShapeId = ShapeId("smithy4s.compliancetests.internals", "TestType")
    def hints: Hints = Hints.empty
    def values: List[TestType] = List(Request, Response)
    case object Request extends TestType("request", 0)
    case object Response extends TestType("response", 0)

    val schema: Schema[TestType] = Schema.stringEnumeration(values)

  }
}
