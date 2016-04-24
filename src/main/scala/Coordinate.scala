case class Coordinate(lat: Double, long: Double)

object Coordinate {
  private  def round(x: Double, n: Int) = ((x * math.pow(10,n) + 0.5).floor / math.pow(10,n))

  def apply(coordinate: String) : Coordinate = coordinate.split(",") match {
    case Array(f1, f2) => Coordinate(round(f1.toDouble,4), round(f2.toDouble, 4))
    case _  => throw new IllegalArgumentException(
      s"Incorrect coordinate $coordinate, it must be two decimal numbers separated by comma(,)")
  }
}
