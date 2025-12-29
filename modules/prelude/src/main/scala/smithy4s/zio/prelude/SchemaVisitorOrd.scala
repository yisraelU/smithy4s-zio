package smithy4s.zio.prelude

import smithy4s.capability.EncoderK
import smithy4s.zio.prelude.instances.all.primOrdPf
import smithy4s.schema._
import smithy4s.{Bijection, Hints, Lazy, Refinement, Schema, ShapeId}
import zio.prelude.Ord

object SchemaVisitorOrd extends CachedSchemaCompiler.Impl[Ord] {
  protected type Aux[A] = Ord[A]
  def fromSchema[A](
      schema: Schema[A],
      cache: Cache
  ): Ord[A] = {
    schema.compile(new SchemaVisitorOrd(cache))
  }
}

final class SchemaVisitorOrd(
    val cache: CompilationCache[Ord]
) extends SchemaVisitor.Cached[Ord] {
  self =>
  override def primitive[P](
      shapeId: ShapeId,
      hints: Hints,
      tag: Primitive[P]
  ): Ord[P] = primOrdPf(tag)

  override def collection[C[_], A](
      shapeId: ShapeId,
      hints: Hints,
      tag: CollectionTag[C],
      member: Schema[A]
  ): Ord[C[A]] = {
    implicit val memberOrd: Ord[A] = self(member)
    tag match {
      case CollectionTag.ListTag => Ord[List[A]]
      case CollectionTag.SetTag  =>
        // Sets compare lexicographically by sorted elements
        Ord[List[A]].contramap((s: Set[A]) =>
          s.toList.sorted(memberOrd.toScala)
        )
      case CollectionTag.VectorTag     => Ord[Vector[A]]
      case CollectionTag.IndexedSeqTag =>
        Ord[Vector[A]].contramap(_.toVector)
    }
  }

  override def map[K, V](
      shapeId: ShapeId,
      hints: Hints,
      key: Schema[K],
      value: Schema[V]
  ): Ord[Map[K, V]] = {
    implicit val ordK: Ord[K] = self(key)
    implicit val ordV: Ord[V] = self(value)
    // Compare maps as sorted sequences of key-value pairs
    Ord[List[(K, V)]].contramap((m: Map[K, V]) =>
      m.toList.sortBy(_._1)(ordK.toScala)
    )
  }

  override def enumeration[E](
      shapeId: ShapeId,
      hints: Hints,
      tag: EnumTag[E],
      values: List[EnumValue[E]],
      total: E => EnumValue[E]
  ): Ord[E] = {
    implicit val enumValueOrd: Ord[EnumValue[E]] =
      tag match {
        case EnumTag.IntEnum() =>
          Ord[Int].contramap(_.intValue)
        case _ =>
          Ord[String].contramap(_.stringValue)
      }

    Ord[EnumValue[E]].contramap(total)
  }

  override def struct[S](
      shapeId: ShapeId,
      hints: Hints,
      fields: Vector[Field[S, ?]],
      make: IndexedSeq[Any] => S
  ): Ord[S] = {
    def forField[A2](field: Field[S, A2]): Ord[S] = {
      field.schema.compile(self).contramap(field.get)
    }
    val ordInstances: Vector[Ord[S]] = fields.map(field => forField(field))

    // Lexicographic ordering: compare fields left-to-right until finding a difference
    (l: S, r: S) => {
      ordInstances.foldLeft(zio.prelude.Ordering.Equals: zio.prelude.Ordering) {
        case (zio.prelude.Ordering.Equals, ord) => ord.compare(l, r)
        case (ordering, _)                      => ordering
      }
    }
  }

  override def union[U](
      shapeId: ShapeId,
      hints: Hints,
      alternatives: Vector[Alt[U, ?]],
      dispatch: Alt.Dispatcher[U]
  ): Ord[U] = {

    // A version of `Ord` that assumes for the compare method that the RHS is "up-casted" to U.
    trait AltOrd[A] {
      def cmp(a: A, u: U): zio.prelude.Ordering
    }

    // The encoded form that Ord works against for the cmp method, is a partially-applied curried function.
    implicit val encoderKInstance: EncoderK[AltOrd, U => zio.prelude.Ordering] =
      new EncoderK[AltOrd, U => zio.prelude.Ordering] {
        def apply[A](fa: AltOrd[A], a: A): U => zio.prelude.Ordering = {
          (u: U) =>
            fa.cmp(a, u)
        }

        def absorb[A](f: A => U => zio.prelude.Ordering): AltOrd[A] =
          new AltOrd[A] {
            def cmp(a: A, u: U): zio.prelude.Ordering = f(a)(u)
          }
      }

    val precompiler = new Alt.Precompiler[AltOrd] {
      def apply[A](label: String, instance: Schema[A]): AltOrd[A] = {
        // Here we "cheat" to recover the `Alt` corresponding to `A`, as this information
        // is lost in the precompiler.
        val altA =
          alternatives.find(_.label == label).get.asInstanceOf[Alt[U, A]]

        // We're using it to get a function that lets us project the `U` against `A`.
        // `U` is not necessarily an `A, so this function returns an `Option`
        val ordA = instance.compile(self)

        // Get the index of this alternative for ordering different alternatives
        val altIndex = alternatives.indexWhere(_.label == label)

        new AltOrd[A] {
          def cmp(a: A, u: U): zio.prelude.Ordering =
            altA.project.lift(u) match {
              case None =>
                // U is not an A - need to compare based on alternative indices
                val uAltIndex =
                  alternatives.indexWhere(alt => alt.project.isDefinedAt(u))
                Ord[Int].compare(altIndex, uAltIndex)
              case Some(a2) =>
                ordA.compare(a, a2) // U is an A, we delegate the comparison
            }
        }
      }
    }
    val altOrdU: AltOrd[U] = dispatch.compile(precompiler)
    (l: U, r: U) => altOrdU.cmp(l, r)
  }

  override def biject[A, B](
      schema: Schema[A],
      bijection: Bijection[A, B]
  ): Ord[B] = {
    val ord = self(schema)
    ord.contramap(bijection.from)
  }

  override def refine[A, B](
      schema: Schema[A],
      refinement: Refinement[A, B]
  ): Ord[B] = {
    val ord = self(schema)
    ord.contramap(refinement.from)
  }

  override def lazily[A](suspend: Lazy[Schema[A]]): Ord[A] = {
    val lazyOrd: Lazy[Ord[A]] = suspend.map(self(_))
    (l: A, r: A) => lazyOrd.value.compare(l, r)
  }

  override def option[A](schema: Schema[A]): Ord[Option[A]] = {
    implicit val ord: Ord[A] = self(schema)
    Ord[Option[A]]
  }
}
