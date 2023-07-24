package treelog

final case class SerializableTree[Annotation](
  label: LogTreeLabel[Annotation],
  children: List[SerializableTree[Annotation]]
)
