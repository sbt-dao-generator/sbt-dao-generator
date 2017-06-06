// --- template_b
case class ${name}(
<#list columns as column>
<#if column.nullable>
${column.propertyName}: Option[${column.propertyType}]<#if column_has_next>,</#if>
<#else>
${column.propertyName}: ${column.propertyType}<#if column_has_next>,</#if>
</#if>
</#list>
) {


}