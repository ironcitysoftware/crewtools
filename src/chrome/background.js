chrome.webRequest.onBeforeRequest.addListener(
  function(details) {
    var parsed = new URL(details.url);
    var caption = parsed.searchParams.get("caption");
    if (caption && caption.includes("The Pilot Vacancy Bid")) {
      return {cancel: true};
    }
  },
  {urls: [
    "https://jia.flica.net/full/OneTimeNotice.cgi*",
    ]},
  ["blocking"]
);
