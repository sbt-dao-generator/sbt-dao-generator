// --- template_a
case class ${name}(
<#list columns as column>
<#if column.nullable>
${column.name}: Option[${column.typeName}]<#if column_has_next>,</#if>
<#else>
${column.name}: ${column.typeName}<#if column_has_next>,</#if>
</#if>
</#list>
) {


}