if (!chrome.cookies) {
  chrome.cookies = chrome.experimental.cookies;
}

function listener(info) {
  if (info.cookie &&
      info.cookie.name &&
     (info.cookie.name.localeCompare("FLiCAService") == 0
        || info.cookie.name.localeCompare("FLiCASession") == 0)) {
	if (info.cookie.sameSite.localeCompare("unspecified") != 0) {
	    info.cookie.sameSite = "";
	    details = {};
	    details.url = "https://jia.flica.net/";
	    details.name = info.cookie.name;
	    details.value = info.cookie.value;
	    details.domain = info.cookie.domain;
	    details.path = info.cookie.path;
	    details.secure = info.cookie.secure;
	    details.httpOnly = info.cookie.httpOnly;
	    details.sameSite = "unspecified";
	    details.expirationDate = info.cookie.expirationDate;
	    details.storeId = info.cookie.storeId;
	    chrome.cookies.set(details);
	}
  }
}

chrome.cookies.onChanged.addListener(listener);


