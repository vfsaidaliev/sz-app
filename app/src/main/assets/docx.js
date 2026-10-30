/* Формирование служебной записки в формате DOCX.
   Оформление: Times New Roman 14, А4, поля 25/20/10/20 мм, по ширине,
   абзацный отступ 1,25 см, одинарный интервал, шапка — таблица без рамок. */
(function(){
var TW=9921; /* ширина текста: 210 мм − 25 мм − 10 мм, в twips */
function esc(s){return String(s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;")}
function r(t,u){return '<w:r>'+(u?'<w:rPr><w:u w:val="single"/></w:rPr>':'')+'<w:t xml:space="preserve">'+esc(t)+'</w:t></w:r>'}
function tab(){return '<w:r><w:tab/></w:r>'}
function p(runs,o){o=o||{};var pr='';
 if(o.tab)pr+='<w:tabs><w:tab w:val="right" w:pos="'+TW+'"/></w:tabs>';
 if(o.ind)pr+='<w:ind w:firstLine="709"/>';
 pr+='<w:jc w:val="'+(o.jc||"both")+'"/>';
 return '<w:p><w:pPr>'+pr+'</w:pPr>'+(runs||'')+'</w:p>'}
function cell(w,content,top){return '<w:tc><w:tcPr><w:tcW w:w="'+w+'" w:type="dxa"/>'+(top?'<w:vAlign w:val="top"/>':'')+'</w:tcPr>'+content+'</w:tc>'}
/* z — структура записки, формируемая make() в index.html */
function build(z){
 var C={jc:"center"},L="";
 z.head.forEach(function(l){L+=p(r(l),C)});
 L+=p("",C)+p(r(z.unit),C)+p("",C)+p(r("СЛУЖЕБНАЯ ЗАПИСКА"),C)+p("",C)+
  p(r(z.date,1)+r(" № ")+r(z.num,1),C)+p("",C)+p(r(z.otn),{jc:"left"});
 var Rc=z.addr.map(function(a){return p(r(a),{jc:"left"})}).join("");
 var none='<w:top w:val="nil"/><w:left w:val="nil"/><w:bottom w:val="nil"/><w:right w:val="nil"/><w:insideH w:val="nil"/><w:insideV w:val="nil"/>';
 var lw=5670,rw=TW-lw;
 var tbl='<w:tbl><w:tblPr><w:tblW w:w="'+TW+'" w:type="dxa"/><w:tblBorders>'+none+'</w:tblBorders><w:tblLayout w:type="fixed"/><w:tblCellMar><w:left w:w="0" w:type="dxa"/><w:right w:w="0" w:type="dxa"/></w:tblCellMar><w:tblLook w:val="0000"/></w:tblPr>'+
  '<w:tblGrid><w:gridCol w:w="'+lw+'"/><w:gridCol w:w="'+rw+'"/></w:tblGrid><w:tr>'+cell(lw,L)+cell(rw,Rc,1)+'</w:tr></w:tbl>';
 var B=p("",{})+p(r(z.greet),C)+p("",{});
 z.body.forEach(function(s){B+=p(r(s),{ind:1})});
 var T=p(r(z.decision),{ind:1})+p("",{});
 if(z.att.length){z.att.forEach(function(s){T+=p(r(s),{})});T+=p("",{})+p("",{})}
 T+=p(r(z.sign[0])+tab()+r(z.sign[1]),{jc:"left",tab:1})+p("",{})+p("",{})+p("",{});
 z.exec.forEach(function(s){T+=p(r(s),{jc:"left"})});
 var sect='<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="567" w:bottom="1134" w:left="1418" w:header="709" w:footer="709" w:gutter="0"/></w:sectPr>';
 return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>'+tbl+B+T+sect+'</w:body></w:document>';
}
var STY='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'+
 '<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:eastAsia="Times New Roman" w:cs="Times New Roman"/><w:sz w:val="28"/><w:szCs w:val="28"/><w:lang w:val="ru-RU" w:eastAsia="ru-RU" w:bidi="ar-SA"/></w:rPr></w:rPrDefault>'+
 '<w:pPrDefault><w:pPr><w:spacing w:before="0" w:after="0" w:line="240" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>'+
 '<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>'+
 '<w:style w:type="table" w:default="1" w:styleId="TableNormal"><w:name w:val="Normal Table"/><w:tblPr><w:tblInd w:w="0" w:type="dxa"/><w:tblCellMar><w:top w:w="0" w:type="dxa"/><w:left w:w="108" w:type="dxa"/><w:bottom w:w="0" w:type="dxa"/><w:right w:w="108" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style></w:styles>';
var CT='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>';
var RELS='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>';
var DRELS='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>';
/* ---- ZIP без сжатия ---- */
function utf8(s){var b=unescape(encodeURIComponent(s)),a=new Uint8Array(b.length);for(var i=0;i<b.length;i++)a[i]=b.charCodeAt(i);return a}
var CRC=(function(){var t=[];for(var n=0;n<256;n++){var c=n;for(var k=0;k<8;k++)c=c&1?0xEDB88320^(c>>>1):c>>>1;t[n]=c>>>0}return t})();
function crc(a){var c=0xFFFFFFFF;for(var i=0;i<a.length;i++)c=CRC[(c^a[i])&255]^(c>>>8);return (c^0xFFFFFFFF)>>>0}
function zip(files){
 var out=[],cen=[],off=0,dt=new Date(),tm=(dt.getHours()<<11)|(dt.getMinutes()<<5)|(dt.getSeconds()>>1),dd=((dt.getFullYear()-1980)<<9)|((dt.getMonth()+1)<<5)|dt.getDate();
 function h(n,l){var a=[];for(var i=0;i<l;i++){a.push(n&255);n=Math.floor(n/256)}return a}
 files.forEach(function(f){
  var nm=utf8(f[0]),d=utf8(f[1]),c=crc(d);
  var lh=[].concat(h(0x04034b50,4),h(20,2),h(0,2),h(0,2),h(tm,2),h(dd,2),h(c,4),h(d.length,4),h(d.length,4),h(nm.length,2),h(0,2));
  out.push(new Uint8Array(lh),nm,d);
  cen.push(new Uint8Array([].concat(h(0x02014b50,4),h(20,2),h(20,2),h(0,2),h(0,2),h(tm,2),h(dd,2),h(c,4),h(d.length,4),h(d.length,4),h(nm.length,2),h(0,2),h(0,2),h(0,2),h(0,2),h(0,4),h(off,4))),nm);
  off+=lh.length+nm.length+d.length;
 });
 var cs=0;cen.forEach(function(x){cs+=x.length});
 var end=new Uint8Array([].concat(h(0x06054b50,4),h(0,2),h(0,2),h(files.length,2),h(files.length,2),h(cs,4),h(off,4),h(0,2)));
 var all=out.concat(cen,[end]),len=0;all.forEach(function(x){len+=x.length});
 var res=new Uint8Array(len),pos=0;all.forEach(function(x){res.set(x,pos);pos+=x.length});return res;
}
function b64(a){var s="";for(var i=0;i<a.length;i+=0x8000)s+=String.fromCharCode.apply(null,a.subarray(i,i+0x8000));return btoa(s)}
window.makeDocx=function(z){
 return b64(zip([["[Content_Types].xml",CT],["_rels/.rels",RELS],["word/document.xml",build(z)],["word/styles.xml",STY],["word/_rels/document.xml.rels",DRELS]]));
};
})();
