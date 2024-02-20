package smithy4s.zio.examples.todo.impl

import zio.Task

trait Database[A] {

  def insert(a: A): Task[Unit]
  def get(id: String): Task[Option[A]]
  def update(id: String, a: A): Task[Unit]
  def delete(id: String): Task[Unit]
  def list(): Task[List[A]]
  def deleteAll(): Task[Unit]
  def count(): Task[Int]

}
