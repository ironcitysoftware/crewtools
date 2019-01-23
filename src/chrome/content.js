var observer = new MutationObserver(function(mutations) {
  for (var i = 0; i < mutations.length; i++) {
    var nodes = mutations[i].addedNodes;
    for (var j = 0; j < nodes.length; j++) {
      var node = nodes[j];
      if (node.nodeType != 1) // only process Node.ELEMENT_NODE
        continue;
      nodes = node.matches(".ui-dialog")
          ? [node]
          : node.querySelectorAll(".ui-dialog");
      [].forEach.call(nodes, function(node) { node.remove() });
    }
  }
});
observer.observe(document, {subtree: true, childList: true});
document.addEventListener("DOMContentLoaded", function() { observer.disconnect() });

//var injectedScript = document.createElement('script');
//injectedScript.src = chrome.extension.getURL('injected.js');
//injectedScript.onload = function() {
//    this.remove();
//};


//$("head script[src*='basicfunc.js']").remove();

//$("head").next()

//$("head script[src*='basicfunc.js']").append(injectedScript);
//(document.head || document.documentElement).appendChild(injectedScript);