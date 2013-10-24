package treelog

case class SerializableTree[Annotation](label: LogTreeLabel[Annotation], children: List[SerializableTree[Annotation]])
