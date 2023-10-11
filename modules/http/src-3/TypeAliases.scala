package smithy4s.zio.http

import smithy4s.Transformation
trait TypeAliases {
  type BiFunctor[Alg[_[_, _, _, _, _]], F[_, _]] =
    Alg[[I, E, O, SI, SO] =>> F[E, O]]

  type BiInterpreter[Op[_, _, _, _, _], F[_, _]] =
    Transformation[Op, [I, E, O, SI, SO] =>> F[E, O]]
}
