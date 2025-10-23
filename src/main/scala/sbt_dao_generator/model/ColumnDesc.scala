package sbt_dao_generator.model

case class ColumnDesc(
    columnName: String,
    typeName: String,
    nullable: Boolean,
    autoIncrement: Boolean,
    columnSize: Option[Int],
    remarks: Option[String],
    generatedColumn: Boolean
)
