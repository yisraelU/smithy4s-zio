package smithy4s.zio.prelude

import smithy4s.capability.EncoderK
import smithy4s.zio.prelude.instances.all.primEqualPf
import smithy4s.schema._
import smithy4s.{Bijection, Hints, Lazy, Refinement, Schema, ShapeId}
import zio.prelude.Equal

object SchemaVisitorEqual extends CachedSchemaCompiler.Impl[Equal] {
  protected type Aux[A] = Equal[A]
  def fromSchema[A](
      schema: Schema[A],
      cache: Cache
  ): Equal[A] = {
    schema.compile(new SchemaVisitorEqual(cache))
  }
}

final class SchemaVisitorEqual(
    val cache: CompilationCache[Equal]
) extends SchemaVisitor.Cached[Equal] {
  self =>
  override def primitive[P](
      shapeId: ShapeId,
      hints: Hints,
      tag: Primitive[P]
  ): Equal[P] = primEqualPf(tag)

  override def collection[C[_], A](
      shapeId: ShapeId,
      hints: Hints,
      tag: CollectionTag[C],
      member: Schema[A]
  ): Equal[C[A]] = {
    implicit val memberHash: Equal[A] = self(member)
    tag match {
      case CollectionTag.ListTag       => Equal[List[A]]
      case CollectionTag.SetTag        => Equal[Set[A]]
      case CollectionTag.VectorTag     => Equal[Vector[A]]
      case CollectionTag.IndexedSeqTag =>
        Equal[scala.collection.immutable.List[A]].contramap(_.toList)
    }
  }

  override def map[K, V](
      shapeId: ShapeId,
      hints: Hints,
      key: Schema[K],
      value: Schema[V]
  ): Equal[Map[K, V]] = {
    implicit val eV = self(value)
    Equal[Map[K, V]]
  }

  override def enumeration[E](
      shapeId: ShapeId,
      hints: Hints,
      tag: EnumTag[E],
      values: List[EnumValue[E]],
      total: E => EnumValue[E]
  ): Equal[E] = {
    implicit val enumValueHash: Equal[EnumValue[E]] =
      tag match {
        case EnumTag.IntEnum() =>
          Equal[Int].contramap(_.intValue)
        case _ =>
          Equal[String].contramap(_.stringValue)
      }

    Equal[EnumValue[E]].contramap(total)
  }

  override def struct[S](
      shapeId: ShapeId,
      hints: Hints,
      fields: Vector[Field[S, _]],
      make: IndexedSeq[Any] => S
  ): Equal[S] = {
    def forField[A2](field: Field[S, A2]): Equal[S] = {
      field.schema.compile(self).contramap(field.get)
    }
    val equalInstances: Vector[Equal[S]] = fields.map(field => forField(field))
    (l: S, r: S) => equalInstances.forall(_.equal(l, r))
  }

  override def union[U](
      shapeId: ShapeId,
      hints: Hints,
      alternatives: Vector[Alt[U, _]],
      dispatch: Alt.Dispatcher[U]
  ): Equal[U] = {

    // A version of `Equal` that assumes for the eqv method that the RHS is "up-casted" to U.
    trait AltEqual[A] {
      def eqv(a: A, u: U): Boolean
    }

    // The encoded form that Hash works against for the eqV method, is a partially-applied curried function.
    implicit val encoderKInstance: EncoderK[AltEqual, U => Boolean] =
      new EncoderK[AltEqual, U => Boolean] {
        def apply[A](fa: AltEqual[A], a: A): U => Boolean = { (u: U) =>
          fa.eqv(a, u)
        }

        def absorb[A](f: A => U => Boolean): AltEqual[A] = new AltEqual[A] {
          def eqv(a: A, u: U): Boolean = f(a)(u)
        }
      }

    val precompiler = new Alt.Precompiler[AltEqual] {
      def apply[A](label: String, instance: Schema[A]): AltEqual[A] = {
        // Here we "cheat" to recover the `Alt` corresponding to `A`, as this information
        // is lost in the precompiler.
        val altA =
          alternatives.find(_.label == label).get.asInstanceOf[Alt[U, A]]

        // We're using it to get a function that lets us project the `U` against `A`.
        // `U` is not necessarily an `A, so this function returns an `Option`
        val eqvA = instance.compile(self)
        new AltEqual[A] {
          def eqv(a: A, u: U): Boolean = altA.project.lift(u) match {
            case None     => false // U is not an A.
            case Some(a2) =>
              eqvA.equal(a, a2) // U is an A, we delegate the comparison
          }
        }
      }
    }
    val altEqualU: AltEqual[U] = dispatch.compile(precompiler)
    (l: U, r: U) => altEqualU.eqv(l, r)
  }

  override def biject[A, B](
      schema: Schema[A],
      bijection: Bijection[A, B]
  ): Equal[B] = {
    val eq = self(schema)
    eq.contramap(bijection.from)
  }

  override def refine[A, B](
      schema: Schema[A],
      refinement: Refinement[A, B]
  ): Equal[B] = {
    val eq = self(schema)
    eq.contramap(refinement.from)
  }

  override def lazily[A](suspend: Lazy[Schema[A]]): Equal[A] = {
    val lazyEq: Lazy[Equal[A]] = suspend.map(self(_))
    (l: A, r: A) => lazyEq.value.equal(l, r)
  }

  override def option[A](schema: Schema[A]): Equal[Option[A]] = {
    implicit val eq: Equal[A] = self(schema)
    Equal[Option[A]]
  }
}
