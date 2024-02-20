package smithy4s.zio.examples.todo.impl

import zio.{Ref, Task}

class InMemoryDatabase[A](
    ref: Ref[Map[String, A]],
    primaryKeyGen: PrimaryKeyGen
) extends Database[A] {
  override def insert(a: A): Task[Unit] = {
    for {
      id <- primaryKeyGen.generate()
      _ <- ref.update(m => m + (id -> a))
    } yield ()
  }

  override def get(id: String): Task[Option[A]] = {
    for {
      map <- ref.get
    } yield map.get(id)
  }

  override def update(id: String, a: A): Task[Unit] = {
    for {
      _ <- ref.update(m => m + (id -> a))
    } yield ()
  }

  override def delete(id: String): Task[Unit] = {
    for {
      _ <- ref.update(m => m - id)
    } yield ()
  }

  override def list(): Task[List[A]] = {
    for {
      map <- ref.get
    } yield map.values.toList
  }

  override def deleteAll(): Task[Unit] = {
    for {
      _ <- ref.update(_ => Map.empty)
    } yield ()
  }

  override def count(): Task[Int] = {
    for {
      map <- ref.get
    } yield map.size
  }
}

object InMemoryDatabase {
  def make[A](primaryKeyGen: PrimaryKeyGen): Task[InMemoryDatabase[A]] = {
    for {
      ref <- Ref.make(Map.empty[String, A])
    } yield new InMemoryDatabase[A](ref, primaryKeyGen)
  }
}
