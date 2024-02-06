package smithy4s

package object example {
  type PizzaAdminService[F[_]] = smithy4s.kinds.FunctorAlgebra[PizzaAdminServiceGen, F]
  val PizzaAdminService = PizzaAdminServiceGen
  type Weather[F[_]] = smithy4s.kinds.FunctorAlgebra[WeatherGen, F]
  val Weather = WeatherGen
  type RecursiveInputService[F[_]] = smithy4s.kinds.FunctorAlgebra[RecursiveInputServiceGen, F]
  val RecursiveInputService = RecursiveInputServiceGen

  type CitySummaries = smithy4s.example.CitySummaries.Type
  type Menu = smithy4s.example.Menu.Type
  type FreeForm = smithy4s.example.FreeForm.Type
  type UVIndex = smithy4s.example.UVIndex.Type
  type CityId = smithy4s.example.CityId.Type
  type Ingredients = smithy4s.example.Ingredients.Type
  type ChanceOfRain = smithy4s.example.ChanceOfRain.Type

}