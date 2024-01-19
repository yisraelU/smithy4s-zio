package smithy4s.zio.prelude
import smithy4s.{Bijection, Hints, Lazy, Refinement, ShapeId}
import smithy4s.capability.EncoderK
import smithy4s.zio.prelude.instances.all.primHashPf
import smithy4s.schema.Schema
import smithy4s.schema._
import zio.prelude.Hash.makeFrom
import zio.prelude.{Equal, Hash}
import zio.prelude.coherent.HashOrd.derive

import scala.util.hashing.MurmurHash3.productSeed

object SchemaVisitorHash extends CachedSchemaCompiler.Impl[Hash] {
  protected type Aux[A] = Hash[A]
  def fromSchema[A](
      schema: Schema[A],
      cache: Cache
  ): Hash[A] = {
    schema.compile(new SchemaVisitorHash(cache))
  }
}

final class SchemaVisitorHash(
    val cache: CompilationCache[Hash]
) extends SchemaVisitor.Cached[Hash] { self =>

  override def primitive[P](
      shapeId: ShapeId,
      hints: Hints,
      tag: Primitive[P]
  ): Hash[P] = primHashPf(tag)

  override def collection[C[_], A](
      shapeId: ShapeId,
      hints: Hints,
      tag: CollectionTag[C],
      member: Schema[A]
  ): Hash[C[A]] = {
    implicit val memberHash: Hash[A] = self(member)
    tag match {
      case CollectionTag.ListTag   => Hash[List[A]]
      case CollectionTag.SetTag    => Hash[Set[A]]
      case CollectionTag.VectorTag => Hash[Vector[A]]
      case CollectionTag.IndexedSeqTag =>
        makeFrom(
          _.map(Hash[A].hash).hashCode,
          Equal.ListEqual.contramap(_.toList)
        )
    }
  }

  override def map[K, V](
      shapeId: ShapeId,
      hints: Hints,
      key: Schema[K],
      value: Schema[V]
  ): Hash[Map[K, V]] = {
    implicit val keyHash: Hash[K] = self(key)
    implicit val valueHash: Hash[V] = self(value)
    Hash[Map[K, V]]
  }

  override def enumeration[E](
      shapeId: ShapeId,
      hints: Hints,
      tag: EnumTag[E],
      values: List[EnumValue[E]],
      total: E => EnumValue[E]
  ): Hash[E] = {
    implicit val enumValueHash: Hash[EnumValue[E]] =
      tag match {
        case EnumTag.IntEnum() =>
          Hash[Int].contramap(_.intValue)
        case _ =>
          Hash[String].contramap(_.stringValue)
      }

    Hash[EnumValue[E]].contramap(total)
  }

  override def struct[S](
      shapeId: ShapeId,
      hints: Hints,
      fields: Vector[Field[S, _]],
      make: IndexedSeq[Any] => S
  ): Hash[S] = {
    def forField[A2](field: Field[S, A2]): Hash[S] = {
      field.schema.compile(self).contramap(field.get)
    }

    val hashInstances: Vector[Hash[S]] = fields.map(field => forField(field))
    // similar to how productPrefix is used
    val nameHash = Hash[String].hash(shapeId.name)
    new Hash[S] {
      def hash(x: S): Int = {
        val hashCodes = hashInstances.map(_.hash(x))
        combineHash(productSeed, nameHash +: hashCodes: _*)
      }

      def checkEqual(l: S, r: S): Boolean = {
        hashInstances.forall(_.equal(l, r))
      }
    }
  }

  override def union[U](
      shapeId: ShapeId,
      hints: Hints,
      alternatives: Vector[Alt[U, _]],
      dispatch: Alt.Dispatcher[U]
  ): Hash[U] = {

    // A version of `Hash` that assumes for the eqv method that the RHS is "up-casted" to U.
    trait AltHash[A] {
      def checkEquals(a: A, u: U): Boolean
      def hash(a: A): Int
    }

    // The encoded form that Hash works against for the eqV method, is a partially-applied curried function.
    implicit val encoderKInstance: EncoderK[AltHash, (U => Boolean, Int)] =
      new EncoderK[AltHash, (U => Boolean, Int)] {
        def apply[A](fa: AltHash[A], a: A): (U => Boolean, Int) = {
          ((u: U) => fa.checkEquals(a, u), fa.hash(a))
        }

        def absorb[A](f: A => (U => Boolean, Int)): AltHash[A] =
          new AltHash[A] {
            def checkEquals(a: A, u: U): Boolean = f(a)._1(u)
            def hash(a: A): Int = f(a)._2
          }
      }

    val precompiler = new Alt.Precompiler[AltHash] {
      def apply[A](label: String, instance: Schema[A]): AltHash[A] = {
        // Here we "cheat" to recover the `Alt` corresponding to `A`, as this information
        // is lost in the precompiler.
        val altA =
          alternatives.find(_.label == label).get.asInstanceOf[Alt[U, A]]

        val labelHash = Hash[String].hash(label)

        // We're using it to get a function that lets us project the `U` against `A`.
        // `U` is not necessarily an `A, so this function returns an `Option`
        val hashA: Hash[A] = instance.compile(self)
        new AltHash[A] {

          override def checkEquals(a: A, u: U): Boolean =
            altA.project.lift(u) match {
              case None => false // U is not an A.
              case Some(a2) =>
                hashA.equal(a, a2) // U is an A, we delegate the comparison
            }
          override def hash(a: A): Int = {
            combineHash(hashA.hash(a), labelHash)
          }
        }
      }
    }

    val altHashU: AltHash[U] = dispatch.compile(precompiler)
    new Hash[U] {
      def checkEqual(x: U, y: U): Boolean = altHashU.equals(x, y)
      def hash(x: U): Int = altHashU.hash(x)
    }
  }

  override def biject[A, B](
      schema: Schema[A],
      bijection: Bijection[A, B]
  ): Hash[B] = {
    implicit val hashA: Hash[A] = self(schema)
    Hash[A].contramap(bijection.from)
  }

  override def refine[A, B](
      schema: Schema[A],
      refinement: Refinement[A, B]
  ): Hash[B] = {
    implicit val hashA: Hash[A] = self(schema)
    Hash[A].contramap(refinement.from)
  }

  override def lazily[A](suspend: Lazy[Schema[A]]): Hash[A] = {
    implicit val hashA: Lazy[Hash[A]] = suspend.map(self(_))
    new Hash[A] {
      override def hash(x: A): Int = hashA.value.hash(x)

      override protected def checkEqual(l: A, r: A): Boolean =
        hashA.value.equal(l, r)
    }
  }

  override def option[A](schema: Schema[A]): Hash[Option[A]] = {
    implicit val hashA: Hash[A] = self(schema)
    Hash[Option[A]]
  }
}
