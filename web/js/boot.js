// Runs before the first paint, so Hebrew pages start right-to-left and the right colors are used
// (the module scripts take over right after). Mirrors resolveLanguage() in i18n.js.
(function () {
  var root = document.documentElement;
  var settings = {};
  try {
    settings = (JSON.parse(localStorage.getItem('shidur:v1') || '{}') || {}).settings || {};
  } catch (e) {}
  var lang = settings.language === 'he' || settings.language === 'en' ? settings.language : 'en';
  if (lang !== settings.language) {
    var tags = navigator.languages || [navigator.language];
    for (var i = 0; i < tags.length; i++) {
      var tag = String(tags[i]).toLowerCase();
      if (tag.indexOf('he') === 0 || tag.indexOf('iw') === 0) {
        lang = 'he';
        break;
      }
      if (tag.indexOf('en') === 0) break;
    }
  }
  root.lang = lang;
  root.dir = lang === 'he' ? 'rtl' : 'ltr';
  if (settings.theme === 'light' || settings.theme === 'dark') root.style.colorScheme = settings.theme;
})();
