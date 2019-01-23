//old_popup = popup;
//popup = function(width, height, url, wname) {
//  //debugger;
//  //alert("hijack: " + width);
//  //return old_popup(width, height, url, wname);
//};
//window.open = function() {
//  alert("redefined window.open");
//};

alert($("div[role='dialog']"));
$("div[role='dialog']").remove();
alert($("div[role='dialog']").attr("class"));

//alert(popup);
//alert("loaded");