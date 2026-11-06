var _debug = false;

// Tonal and Shift / Caps Lock State Machine
var _ishTonalActive = false;
var _ishCapsLocked = false;
var _ishLastShiftTapTime = 0;
var _ishShiftWordActive = false;

var VOWEL_MAP = {
  'K_A': { lower: 'a', lowerTonal: 'â', upper: 'A', upperTonal: 'Â' },
  'K_E': { lower: 'e', lowerTonal: 'ê', upper: 'E', upperTonal: 'Ê' },
  'K_I': { lower: 'i', lowerTonal: 'î', upper: 'I', upperTonal: 'Î' },
  'K_O': { lower: 'o', lowerTonal: 'ô', upper: 'O', upperTonal: 'Ô' },
  'K_U': { lower: 'u', lowerTonal: 'û', upper: 'U', upperTonal: 'Û' }
};

var TONAL_VOWELS = {
  'a': 'â', 'e': 'ê', 'i': 'î', 'o': 'ô', 'u': 'û',
  'A': 'Â', 'E': 'Ê', 'I': 'Î', 'O': 'Ô', 'U': 'Û'
};

var NON_TONAL_KEYS = [
  'K_Q', 'K_W', 'K_R', 'K_T', 'K_Y', 'K_P',
  'K_S', 'K_D', 'K_F', 'K_G', 'K_H', 'K_J', 'K_K', 'K_L',
  'K_Z', 'K_X', 'K_C', 'K_V', 'K_B', 'K_N', 'K_M'
];

function tagKeys() {
  try {
    for (var i = 0; i < NON_TONAL_KEYS.length; i++) {
      var elems = document.querySelectorAll('[id$="-' + NON_TONAL_KEYS[i] + '"]');
      for (var j = 0; j < elems.length; j++) {
        elems[j].classList.add('ish-non-tonal');
      }
    }
    for (var keyId in VOWEL_MAP) {
      var vElems = document.querySelectorAll('[id$="-' + keyId + '"]');
      for (var k = 0; k < vElems.length; k++) {
        vElems[k].classList.add('ish-vowel-key');
      }
    }
  } catch(e) {}
}

function syncTonalAndCapsVisuals() {
  try {
    tagKeys();

    // 1. Tonal Mode Body Class & Tonal Key highlight
    var tonalBtns = document.querySelectorAll('[id$="-K_TONAL"]');
    if (_ishTonalActive) {
      document.body.classList.add('ish-tonal-mode');
      for (var i = 0; i < tonalBtns.length; i++) {
        tonalBtns[i].classList.add('ish-tonal-active');
      }
    } else {
      document.body.classList.remove('ish-tonal-mode');
      for (var i = 0; i < tonalBtns.length; i++) {
        tonalBtns[i].classList.remove('ish-tonal-active');
      }
    }

    // 2. Vowel key labels - default layer ALWAYS lowercase, shift layer ALWAYS uppercase!
    for (var keyId in VOWEL_MAP) {
      var v = VOWEL_MAP[keyId];
      var defKey = document.getElementById('default-' + keyId);
      if (defKey) {
        var defSpan = defKey.querySelector('.kmw-key-text');
        if (defSpan) {
          defSpan.textContent = _ishTonalActive ? v.lowerTonal : v.lower;
          defSpan.style.color = '#ffffff';
          defSpan.style.fontWeight = 'normal';
        }
      }
      var shiftKey = document.getElementById('shift-' + keyId);
      if (shiftKey) {
        var shiftSpan = shiftKey.querySelector('.kmw-key-text');
        if (shiftSpan) {
          shiftSpan.textContent = _ishTonalActive ? v.upperTonal : v.upper;
          shiftSpan.style.color = '#ffffff';
          shiftSpan.style.fontWeight = 'normal';
        }
      }
    }

    // 3. Tonal key subtle musical note SVG injection
    var tonalKeys = document.querySelectorAll('[id*="K_TONAL"]');
    var tonalSvg = '<svg class="ish-tonal-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#ffffff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:inline-block; vertical-align:middle; pointer-events:none;"><circle cx="8" cy="17" r="3" fill="#ffffff"/><path d="M11 17V5l8-1v4"/></svg>';
    for (var tIdx = 0; tIdx < tonalKeys.length; tIdx++) {
      var tSpan = tonalKeys[tIdx].querySelector('.kmw-key-text');
      if (tSpan && tSpan.innerHTML.indexOf('ish-tonal-icon') === -1) {
        tSpan.innerHTML = tonalSvg;
      }
    }

    // 4. Enter key icon SVG injection
    var enterKeys = document.querySelectorAll('[id*="K_ENTER"]');
    var enterSvg = '<svg viewBox="0 0 24 24" width="22" height="22" stroke="#ffffff" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round" style="display:inline-block; vertical-align:middle; pointer-events:none;"><polyline points="9 10 4 15 9 20"/><path d="M20 4v7a4 4 0 0 1-4 4H4"/></svg>';
    for (var eIdx = 0; eIdx < enterKeys.length; eIdx++) {
      var enterSpan = enterKeys[eIdx].querySelector('.kmw-key-text');
      if (enterSpan && enterSpan.innerHTML.indexOf('<svg') === -1) {
        enterSpan.innerHTML = enterSvg;
      }
    }

    // 5. Shift key visual indicator
    var osk = (keyman && keyman.osk) ? keyman.osk : null;
    var currentLayerId = (osk && osk.vkbd) ? osk.vkbd.layerId : 'default';
    var isShiftOn = (currentLayerId === 'shift') || _ishShiftWordActive;

    var shiftBtns = document.querySelectorAll('[id$="-K_SHIFT"]');
    for (var s = 0; s < shiftBtns.length; s++) {
      if (_ishCapsLocked) {
        shiftBtns[s].classList.add('ish-caps-locked');
        shiftBtns[s].classList.add('kmw-key-shift-on');
      } else if (isShiftOn) {
        shiftBtns[s].classList.remove('ish-caps-locked');
        shiftBtns[s].classList.add('kmw-key-shift-on');
      } else {
        shiftBtns[s].classList.remove('ish-caps-locked');
        shiftBtns[s].classList.remove('kmw-key-shift-on');
      }
    }
  } catch(err) {
    console.warn("syncTonalAndCapsVisuals error: " + err);
  }
}

function hookLayerChanges() {
  try {
    if (keyman && keyman.osk && keyman.osk.vkbd && keyman.osk.vkbd._UpdateVKShiftStyle) {
      if (keyman.osk.vkbd._ishHooked) return;
      keyman.osk.vkbd._ishHooked = true;
      var orig = keyman.osk.vkbd._UpdateVKShiftStyle.bind(keyman.osk.vkbd);
      keyman.osk.vkbd._UpdateVKShiftStyle = function(layerName) {
        var res = orig(layerName);
        if (_ishCapsLocked && layerName !== 'shift' && layerName !== 'numeric' && layerName !== 'symbols') {
          try {
            keyman.osk.vkbd.layerId = 'shift';
            keyman.osk.vkbd.lockLayer(true);
            if (keyman.core && keyman.core.keyboardProcessor) {
              keyman.core.keyboardProcessor.layerId = 'shift';
            }
          } catch(e) {}
        }
        syncTonalAndCapsVisuals();
        return res;
      };
    }
  } catch(e) {}
}

function updateTonalVisuals(active) {
  _ishTonalActive = !!active;
  syncTonalAndCapsVisuals();
}

function updateShiftKeyVisual(isLocked) {
  _ishCapsLocked = !!isLocked;
  syncTonalAndCapsVisuals();
}

// Android harness attachment
if(window.parent && window.parent.jsInterface && !window.jsInterface) {
  window.jsInterface = window.parent.jsInterface;
}

var device = window.jsInterface.getDeviceType();
var oskHeight = Math.ceil(window.jsInterface.getKeyboardHeight() / window.devicePixelRatio);
var oskWidth = 0;
var bannerHeight = 0;
var bannerImagePath = '';
var bannerHTMLContents = '';
var fragmentToggle = 0;
var deferredBannerCall;

var sentryManager = new KeymanSentryManager({
  hostPlatform: "android"
});
sentryManager.init();

window.addEventListener('load', init, false);

function loadDefaultKeyboard() {
  notifyHost('reloadAfterError');
}

function init() {
  //document.body.style.backgroundColor="transparent";
  //window.console.log('Device type = '+device);
  //window.console.log('Keyboard height = '+oskHeight);
  keyman.showKeyboardList = showMenu;
  keyman.hideKeyboard = hideKeyboard;
  keyman.menuKeyUp = menuKeyUp;
  keyman.getOskHeight = getOskHeight;
  keyman.getOskWidth = getOskWidth;
  keyman.beepKeyboard = beepKeyboard;

  // Readies the keyboard stub for instant loading during the init process.
  try {
    let starterStubStr = jsInterface.initialKeyboard();
    if(starterStubStr) {
      KeymanWeb.registerStub(JSON.parse(starterStubStr));
    }
  } catch (err) {
    // If a Java exception triggers during this time... okay, we can't
    // load a keyboard during startup.  Rely on older loading techniques.
    //
    // Note:  no information about the Java-side error is available here.
    if(err.message.indexOf("Java exception") < 0) {
      throw err;
    }
  }

  keyman.init({
    'embeddingApp':device,
    'fonts':'packages/',
    oninserttext: insertText,
    root:'./'
  }).then(function () {  // Note:  For non-upgraded API 21, arrow functions will break the keyboard!
    bannerHeight = Math.ceil(window.jsInterface.getDefaultBannerHeight() / window.devicePixelRatio);
    if (bannerHeight > 0) {

      // The OSK is not available until initialization is complete.
      keyman.osk.bannerView.activeBannerHeight = bannerHeight;

      if(deferredBannerCall) {
        deferredBannerCall();
        deferredBannerCall = null;
      }

      keyman.refreshOskLayout();
    }
    installIshSuggestionFilter();
    initSpacebarTrackpad();
    setTimeout(function() {
      hookLayerChanges();
      tagKeys();
      syncTonalAndCapsVisuals();
    }, 100);
    setTimeout(loadIshFusedMap, 50);
  });

  function installIshSuggestionFilter() {
    try {
      var pc = keyman.contextManager && keyman.contextManager.predictionContext;
      if (pc && pc.sendUpdateEvent) {
        if (pc._ishFilterInstalled) return;
        pc._ishFilterInstalled = true;

        var _ishLastAcceptedTime = 0;
        var _ishLastAcceptedWord = '';

        // Deterministic, direct suggestion acceptance:
        // Replaces the active word token before the cursor atomically via Android's InputConnection.
        // Completely eliminates Keyman's buggy diff engine that caused partial prefix leftovers (e.g. nanNang) and duplicate insertions.
        pc.accept = function(suggestion) {
          if (!suggestion) return Promise.resolve(null);
          var now = Date.now();
          var chosenWord = (suggestion.displayAs || (suggestion.transform && suggestion.transform.insert) || "").trim();
          if (!chosenWord) return Promise.resolve(null);

          // Debounce / deduplication latch: prevent double-accept within 400ms
          if (now - _ishLastAcceptedTime < 400) {
            console.log("ISH: Dropped duplicate suggestion accept within 400ms: " + chosenWord);
            return Promise.resolve(suggestion);
          }
          _ishLastAcceptedTime = now;
          _ishLastAcceptedWord = chosenWord;

          // Record if the accepted suggestion is a literal override (Option 1 in 2-Slot Banner)
          try {
            var isLit = !!(suggestion.isLiteral || (suggestion.sample && suggestion.sample.isLiteral) || suggestion.p === 0.1 || (suggestion.sample && suggestion.sample.p === 0.1));
            var disp = (suggestion.displayAs || (suggestion.sample && suggestion.sample.displayAs) || '').trim();
            if (isLit && disp) {
              _ishLiteralAcceptedWord = disp.toLowerCase();
              console.log("ISH: Literal override accepted for token: " + _ishLiteralAcceptedWord);
            } else {
              _ishLiteralAcceptedWord = null;
            }
          } catch(litErr) {}

          // 1. Get true current text before cursor directly from Android InputConnection
          var bridgeText = (window.ishBridge && window.ishBridge.getTextBeforeCursor) ? window.ishBridge.getTextBeforeCursor(100) : '';
          var tokenMatch = bridgeText.match(/[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$/);
          var curToken = tokenMatch ? tokenMatch[0] : '';
          if (!curToken && keyman && keyman.context && keyman.context.getTextBeforeCaret) {
            var kmText = keyman.context.getTextBeforeCaret() || '';
            var kmMatch = kmText.match(/[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$/);
            if (kmMatch) curToken = kmMatch[0];
          }

          // 2. Exact deletion: delete the entire active word token before cursor
          var dn = curToken ? curToken.length : 0;
          var s = chosenWord + ' ';

          console.log("ISH Suggestion Accept: replacing '" + curToken + "' (dn=" + dn + ") with '" + s + "'");

          // 3. Atomically replace via Android bridge
          if (window.jsInterface && window.jsInterface.insertText) {
            window.jsInterface.insertText(dn, s, 0, false);
          }

          // 4. Synchronize Keyman internal context
          try {
            if (keyman && keyman.context) {
              var before = keyman.context.getTextBeforeCaret() || '';
              var after = keyman.context.getTextAfterCaret() || '';
              if (curToken && before.endsWith(curToken)) {
                var newBefore = before.slice(0, before.length - curToken.length) + s;
                keyman.context.setText(newBefore + after);
                keyman.context.setSelection(newBefore.length, newBefore.length);
              } else {
                keyman.context.setText(before + s + after);
                keyman.context.setSelection((before + s).length, (before + s).length);
              }
              if (keyman.context.saveState) keyman.context.saveState();
            }
          } catch(ctxErr) {
            console.warn("Keyman context sync error: " + ctxErr);
          }

          // 5. Clear suggestions from banner and reset Keyman LM context
          if (pc.clearSuggestions) {
            pc.clearSuggestions();
          }
          setTimeout(function() {
            try {
              if (keyman && keyman.resetContext) {
                keyman.resetContext();
              } else if (keyman && keyman.contextManager && keyman.contextManager.resetContext) {
                keyman.contextManager.resetContext();
              }
            } catch(err) {}
          }, 10);

          // 6. Reset Shift state if single-shot Shift was active
          _ishShiftWordActive = false;
          if (!_ishCapsLocked) {
            var vkbd = (keyman && keyman.osk && keyman.osk.vkbd) ? keyman.osk.vkbd : null;
            if (vkbd && vkbd.layerId === 'shift') {
              vkbd.lockLayer(false);
              vkbd.layerId = 'default';
              if (keyman.core && keyman.core.keyboardProcessor) {
                keyman.core.keyboardProcessor.layerId = 'default';
              }
              syncTonalAndCapsVisuals();
            }
          }

          // 7. Tactile feedback
          if (window.ishBridge && window.ishBridge.onKeyTap) {
            window.ishBridge.onKeyTap();
          }

          // 8. Track bigram transition
          try {
            var prevText = bridgeText.slice(0, bridgeText.length - (curToken ? curToken.length : 0)).trim();
            var prevMatch = prevText.match(/([a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)$/);
            if (prevMatch && prevMatch[1] && window.ishBridge && window.ishBridge.onWordTransition) {
              window.ishBridge.onWordTransition(prevMatch[1], chosenWord);
            }
          } catch(transErr) {}

          return Promise.resolve(suggestion);
        };

        var origSend = pc.sendUpdateEvent.bind(pc);
        pc.sendUpdateEvent = function() {
          var rawList = this.currentSuggestions ? this.currentSuggestions.slice() : [];
          var validTransformId = (rawList.length > 0 && rawList[0].transformId) ? rawList[0].transformId : 1;

          var bridgeCursorText = (window.ishBridge && window.ishBridge.getTextBeforeCursor) ? window.ishBridge.getTextBeforeCursor(40) : '';

          var curTokenMatch = bridgeCursorText.match(/[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$/);
          var curToken = curTokenMatch ? curTokenMatch[0] : '';
          if (!curToken && keyman && keyman.context && keyman.context.getTextBeforeCaret) {
            var kmText = keyman.context.getTextBeforeCaret() || '';
            var kmMatch = kmText.match(/[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$/);
            if (kmMatch) curToken = kmMatch[0];
          }

          var spaceMatch = !curToken ? bridgeCursorText.match(/([a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)\s+$/) : null;
          var prevWord = spaceMatch ? spaceMatch[1] : '';

          var finalSuggestions = [];
          var exactAutoCorrectObj = null;

          // Determine target casing mode for suggestions
          var vkbd = (keyman && keyman.osk && keyman.osk.vkbd) ? keyman.osk.vkbd : null;
          var isShiftLayer = (vkbd && vkbd.layerId === 'shift') ||
                             (keyman.core && keyman.core.keyboardProcessor && keyman.core.keyboardProcessor.layerId === 'shift');

          var curTargetCase = 'lower';
          if (_ishCapsLocked) {
            curTargetCase = 'upper';
          } else if (isShiftLayer || _ishShiftWordActive) {
            curTargetCase = 'title';
          } else if (curToken && curToken.length >= 2 && curToken === curToken.toUpperCase()) {
            curTargetCase = 'upper';
          } else if (curToken && /^[A-Z\u00C0-\u024F\u1E6C]/.test(curToken)) {
            curTargetCase = 'title';
          }

          if (curToken && curToken.length >= 1 && window.ishBridge && window.ishBridge.getPersonalSuggestions) {
            try {
              var pJson = window.ishBridge.getPersonalSuggestions(curToken, 4);
              var pList = JSON.parse(pJson);
              if (pList && pList.length > 0) {
                for (var pi = 0; pi < pList.length; pi++) {
                  var pItem = pList[pi];
                  var pWord = pItem.replacement || pItem.word;
                  var formattedWord = applyCasing(pWord, curTargetCase);
                  var suggObj = {
                    displayAs: formattedWord,
                    transform: {
                      deleteHierarchy: curToken.length,
                      insert: formattedWord + ' '
                    },
                    transformId: validTransformId,
                    isExactReplacement: !!pItem.isExactReplacement,
                    isLiteral: !!pItem.isLiteral
                  };

                  var alreadyIn = finalSuggestions.some(function(s) {
                    return (s.displayAs || '').toLowerCase() === formattedWord.toLowerCase();
                  });
                  if (!alreadyIn) {
                    finalSuggestions.push(suggObj);
                    if (pItem.isExactReplacement && !exactAutoCorrectObj) {
                      exactAutoCorrectObj = suggObj;
                    }
                  }
                }
              }
            } catch (pErr) {
              console.warn("Personal suggestion injection error: " + pErr);
            }
          } else if (prevWord && window.ishBridge && window.ishBridge.getNextWordPredictions) {
            // Next-word prediction stage (e.g. after "saruok ")
            try {
              var nwJson = window.ishBridge.getNextWordPredictions(prevWord, 3);
              var nwList = JSON.parse(nwJson);
              if (nwList && nwList.length > 0) {
                for (var nwi = 0; nwi < nwList.length; nwi++) {
                  var nw = nwList[nwi];
                  var formattedNw = applyCasing(nw, curTargetCase);
                  finalSuggestions.push({
                    displayAs: formattedNw,
                    transform: {
                      deleteHierarchy: 0,
                      insert: formattedNw + ' '
                    },
                    transformId: validTransformId
                  });
                }
              }
            } catch (nwErr) {
              console.warn("Next-word prediction injection error: " + nwErr);
            }
          }

          // Fill up remaining slots (up to 3) from Keyman's engine suggestions
          for (var ri = 0; ri < rawList.length && finalSuggestions.length < 3; ri++) {
            var cand = rawList[ri];
            var d = (cand.displayAs || (cand.transform && cand.transform.insert) || "").trim();
            if (!d) continue;
            var formattedD = applyCasing(d, curTargetCase);
            var already = finalSuggestions.some(function(s) {
              return (s.displayAs || '').toLowerCase() === formattedD.toLowerCase();
            });
            if (!already) {
              var dnVal = (cand.transform && typeof cand.transform.deleteHierarchy === 'number')
                          ? cand.transform.deleteHierarchy : (curToken ? curToken.length : 0);
              finalSuggestions.push({
                displayAs: formattedD,
                transform: {
                  deleteHierarchy: dnVal,
                  insert: formattedD + ' '
                },
                transformId: validTransformId
              });
            }
          }

          // Auto-correction binding on space:
          // Do not set this.selected = exactAutoCorrectObj to prevent KeymanWeb's internal
          // doTryAccept from firing concurrently with the spacebar keystroke.
          // Exact replacements and auto-splits are cleanly handled once in insertText.
          this.selected = null;

          if (finalSuggestions.length > 0) {
            this.emit("update", finalSuggestions.slice(0, 3));
            return;
          }

          origSend();
        };
        console.log("ISH 3-slot suggestion filter with auto-correct and tap fallback successfully installed!");
      }
    } catch (e) {
      console.warn("Could not install ISH suggestion filter: " + e);
    }
  }

  keyman.addEventListener('keyboardloaded', function(e) {
    setIsChiral(e);
    installIshSuggestionFilter();
  });
  keyman.addEventListener('keyboardchange', setIsChiral);
  keyman.core.languageProcessor.on('statechange', onStateChange);

  document.body.addEventListener('touchend', loadDefaultKeyboard);

  notifyHost('pageLoaded');
}

function showBanner(flag) {
  if(!keyman.osk) {
    deferredBannerCall = function() {
      showBanner(flag);
    }

    return;
  }

  var bc = keyman.osk.bannerController;

  console_debug("Setting banner display for dictionaryless keyboards to " + flag);
  console_debug("bannerHTMLContents: " + bannerHTMLContents);
  if(bc) {
    if (bannerHTMLContents != '') {
      bc.inactiveBanner = flag ? new bc.HTMLBanner(bannerHTMLContents) : null;
    } else {
      bc.inactiveBanner = flag ? new bc.ImageBanner(bannerImagePath) : null;
    }
  }
}

function setBannerImage(path) {
  bannerImagePath = path;
}

// Set the HTML banner to use when predictive-text is not available
// contents - HTML content to use for the banner
function setBannerHTML(contents) {
  bannerHTMLContents = contents;
}

function notifyHost(event, params) {
  console_debug('notifyHost(event='+event+',params='+params+')');
  // TODO: Update all other host notifications to use notifyHost instead of directly setting window.location.hash
  window.setTimeout(function() {
    // We use a timeout so that the navigation doesn't cause the calling function to abort after the call
    fragmentToggle = (fragmentToggle + 1) % 100;
    params = params ? '+'+params : '';
    window.location.hash = event+'-'+fragmentToggle+params;
  }, 10);
}

// Update the KMW banner height
// h is in dpi (different from iOS)
function setBannerHeight(h) {
  if (h > 0) {
    // The banner itself may not be loaded yet.  This will preemptively help set
    // its eventual display height.
    bannerHeight = Math.ceil(h / window.devicePixelRatio);

    if (keyman.osk) {
      keyman.osk.bannerView.activeBannerHeight = bannerHeight;
    }
  }

  // Refresh KMW's OSK
  keyman.refreshOskLayout();
}

function setOskHeight(h) {
  if(h > 0) {
    oskHeight = Math.ceil(h / window.devicePixelRatio);
  }
  if(keyman && keyman.core && keyman.core.activeKeyboard) {
    keyman.core.activeKeyboard.refreshLayouts();
  }
  keyman.refreshOskLayout();
}

function setOskWidth(w) {
  if(w > 0) {
    oskWidth = w / window.devicePixelRatio;
  }
}

function getOskHeight() {
  // banner height is calculated completely separately, unlike iOS
  return oskHeight;
}

function getOskWidth() {
  if(oskWidth <= 0) {
    oskWidth = window.jsInterface.getKeyboardWidth();
  }
  //window.console.log('Keyboard width = '+oskWidth);
  return oskWidth;
}

function beepKeyboard() {
  window.jsInterface.beepKeyboard();
}

function onStateChange(change) {
  console.log('onStateChange change: ' + change);

  // Refresh KMW OSK
  keyman.refreshOskLayout();

  fragmentToggle = (fragmentToggle + 1) % 100;
  window.location.hash = 'refreshBannerHeight-'+fragmentToggle;
}

// Query KMW if a given keyboard uses chiral modifiers.
function setIsChiral(keyboardProperties) {
  var name = typeof(keyboardProperties.internalName) == 'undefined' ? keyboardProperties.keyboardName : keyboardProperties.internalName;
  var isChiral = keyman.isChiral(name);
  window.jsInterface.setIsChiral(isChiral);
  return true;
}

function setKeymanLanguage(k) {
  KeymanWeb.registerStub(k);
  keyman.setActiveKeyboard(k.KI, k.KLC);
}

function setSpacebarText(mode) {
  var text = (mode == undefined) || !mode.text ? '' : mode.text;
  keyman.config.spacebarText = text;
}

// #6665: we need to know when the user has pressed a hardware key so we don't
// generate haptic feedback when text changes are made via insertText. We know
// that the executeHardware->insertText process is a non-reentrant
// single-threaded sequence so this global flag tracking whether we are
// currently executing a hardware keystroke event is safe here. We can't do this
// on the Java side, because the Java<-->JS interface is asynchronous, and we
// cannot reliably know when each hardware keystroke has completed processing.
var executingHardwareKeystroke = false;

// Cache for the ISH lexical model code — 11.7MB asset, only loaded once
var _ishModelCodeCache = {};

var _ishFusedMap = null;
function loadIshFusedMap() {
  if (_ishFusedMap) return _ishFusedMap;
  try {
    if (window.ishBridge && window.ishBridge.getFusedMap) {
      var jsonStr = window.ishBridge.getFusedMap();
      if (jsonStr) {
        _ishFusedMap = JSON.parse(jsonStr);
        console.log("Loaded ISH fused map with " + Object.keys(_ishFusedMap).length + " entries!");
      }
    }
  } catch (e) {
    console.warn("Failed to load ISH fused map: " + e);
  }
  return _ishFusedMap;
}

function toTitleCase(s) {
  if (!s || typeof s !== 'string') return s;
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();
}

function applyCasing(word, targetCase) {
  if (!word || typeof word !== 'string') return word;
  if (targetCase === 'upper') {
    return word.toUpperCase();
  } else if (targetCase === 'title') {
    return toTitleCase(word);
  } else {
    return word.toLowerCase();
  }
}

function getWordCasing(word) {
  if (!word || typeof word !== 'string' || word.length === 0) return 'none';
  if (word.length >= 2 && word === word.toUpperCase()) return 'upper';
  if (word.length === 1 && word === word.toUpperCase()) return 'title';
  if (/^[A-Z\u00C0-\u024F\u1E6C]/.test(word)) return 'title';
  return 'lower';
}

function matchCase(original, target) {
  if (!original || !target) return target;
  if (original === original.toUpperCase()) {
    return target.toUpperCase();
  }
  if (original[0] === original[0].toUpperCase()) {
    return toTitleCase(target);
  }
  return target;
}

var _ishScrubJustFinished = false;
var _ishLiteralAcceptedWord = null;
function initSpacebarTrackpad() {
  var isScrubbing = false;
  var scrubStartX = 0;
  var scrubStartY = 0;
  var lastX = 0;
  var holdTimer = null;
  var spacebarElem = null;

  try {
    var style = document.createElement('style');
    style.id = 'ish-trackpad-style';
    style.innerHTML = 
      '.ish-scrubber-active {' +
      '  background-color: #3b4d66 !important;' +
      '  box-shadow: inset 0 0 10px rgba(100, 140, 180, 0.4) !important;' +
      '  border-radius: 4px !important;' +
      '}' +
      'body.ish-trackpad-scrubbing .kmw-key-text,' +
      'body.ish-trackpad-scrubbing .kmw-key-subscript,' +
      'body.ish-trackpad-scrubbing .kmw-key span,' +
      'body.ish-trackpad-scrubbing .kmw-key .kmw-key-label,' +
      'body.ish-trackpad-scrubbing .kmw-spacebar-caption,' +
      'body.ish-trackpad-scrubbing .kmw-key::after {' +
      '  opacity: 0 !important;' +
      '  visibility: hidden !important;' +
      '  transition: opacity 0.1s ease !important;' +
      '}' +
      '.kmw-key[id*="K_EMOJI"] {' +
      '  background-image: url("data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjZDBlMmQzIiBzdHJva2Utd2lkdGg9IjIiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCI+PGNpcmNsZSBjeD0iMTIiIGN5PSIxMiIgcj0iOS41Ii8+PHBhdGggZD0iTTggMTQuNWMxIDEuOCAyLjUgMi41IDQgMi41czMtLjcgNC0yLjUiLz48Y2lyY2xlIGN4PSI5IiBjeT0iOS41IiByPSIxLjIiIGZpbGw9IiNkMGUyZDMiIHN0cm9rZT0ibm9uZSIvPjxjaXJjbGUgY3g9IjE1IiBjeT0iOS41IiByPSIxLjIiIGZpbGw9IiNkMGUyZDMiIHN0cm9rZT0ibm9uZSIvPjwvc3ZnPg==") !important;' +
      '  background-repeat: no-repeat !important;' +
      '  background-position: center !important;' +
      '  background-size: 22px 22px !important;' +
      '}' +
      '.kmw-key[id*="K_EMOJI"] .kmw-key-text,' +
      '.kmw-key[id*="K_EMOJI"] span {' +
      '  display: none !important;' +
      '}' +
      'body.ish-trackpad-scrubbing .kmw-key[id*="K_EMOJI"] {' +
      '  background-image: none !important;' +
      '}' +
      '.kmw-key[id*="K_ENTER"] {' +
      '  display: flex !important;' +
      '  align-items: center !important;' +
      '  justify-content: center !important;' +
      '}' +
      '.kmw-key[id*="K_ENTER"] .kmw-key-text {' +
      '  display: flex !important;' +
      '  align-items: center !important;' +
      '  justify-content: center !important;' +
      '  width: 100% !important;' +
      '  height: 100% !important;' +
      '}' +
      '.kmw-key[id*="K_TONAL"] {' +
      '  display: flex !important;' +
      '  align-items: center !important;' +
      '  justify-content: center !important;' +
      '  color: #ffffff !important;' +
      '  transition: background-color 0.15s ease !important;' +
      '}' +
      '.kmw-key[id*="K_TONAL"] .kmw-key-text {' +
      '  display: flex !important;' +
      '  align-items: center !important;' +
      '  justify-content: center !important;' +
      '  width: 100% !important;' +
      '  height: 100% !important;' +
      '}' +
      '.kmw-key.ish-tonal-active,' +
      '.phone.android .kmw-key.ish-tonal-active {' +
      '  background-color: #3b4d66 !important;' +
      '  color: #ffffff !important;' +
      '  box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, 0.25) !important;' +
      '}' +
      '.kmw-key.ish-caps-locked,' +
      '.phone.android .kmw-key.ish-caps-locked {' +
      '  background-color: #3b4d66 !important;' +
      '  box-shadow: inset 0 -3px 0 #ffffff !important;' +
      '}' +
      '.phone.android .kmw-key.kmw-key-shift-on,' +
      '.kmw-key.kmw-key-shift-on {' +
      '  background-color: #3b4d66 !important;' +
      '}' +
      '.kmw-key.kmw-key-touched,' +
      '.phone.android .kmw-key.kmw-key-touched,' +
      '.phone.android .kmw-key.kmw-spacebar.kmw-key-touched,' +
      '.kmw-spacebar.kmw-key-touched {' +
      '  background-color: #3d4756 !important;' +
      '}' +
      'body.ish-tonal-mode .kmw-key.ish-non-tonal .kmw-key-text,' +
      'body.ish-tonal-mode .kmw-key.ish-non-tonal .kmw-key-subscript,' +
      'body.ish-tonal-mode .kmw-key.ish-non-tonal span {' +
      '  opacity: 0 !important;' +
      '  visibility: hidden !important;' +
      '}' +
      '#kmw-popup-shim {' +
      '  background-color: rgba(0, 0, 0, 0.45) !important;' +
      '}' +
      '#kmw-popup-keys,' +
      '.phone.android #kmw-popup-keys {' +
      '  background-color: #333333 !important;' +
      '  border: 1px solid #3b4d66 !important;' +
      '  border-radius: 6px !important;' +
      '  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.6) !important;' +
      '  padding: 3px 3px 2px 3px !important;' +
      '  z-index: 10005 !important;' +
      '}' +
      '#kmw-popup-callout,' +
      '.phone.android #kmw-popup-callout {' +
      '  border-top-color: #333333 !important;' +
      '  filter: drop-shadow(0 2px 3px rgba(0, 0, 0, 0.4)) !important;' +
      '}' +
      '#kmw-popup-keys .kmw-key-square-ex {' +
      '  margin: 1px 2px !important;' +
      '}' +
      '#kmw-popup-keys .kmw-key,' +
      '.phone.android #kmw-popup-keys .kmw-key {' +
      '  background-color: #777777 !important;' +
      '  border: none !important;' +
      '  border-bottom: solid 1px #8a8d90 !important;' +
      '  border-radius: 3px !important;' +
      '  color: #ffffff !important;' +
      '  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.25) !important;' +
      '  transition: background-color 0.1s ease !important;' +
      '}' +
      '#kmw-popup-keys .kmw-key .kmw-key-text {' +
      '  font-size: 1.1em !important;' +
      '  font-weight: 500 !important;' +
      '  color: #ffffff !important;' +
      '}' +
      '#kmw-popup-keys .kmw-key.kmw-key-touched,' +
      '.phone.android #kmw-popup-keys .kmw-key.kmw-key-touched {' +
      '  background-color: #3b4d66 !important;' +
      '  border-bottom: solid 1px #5a7090 !important;' +
      '  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.2) !important;' +
      '  transform: none !important;' +
      '}';
    document.head.appendChild(style);
  } catch(e) {}

  // Direct touch listener for instant key haptics on any keyboard key or suggestion banner item
  document.addEventListener('touchstart', function(e) {
    if (!e.touches || e.touches.length === 0) return;
    var touch = e.touches[0];
    var target = document.elementFromPoint(touch.clientX, touch.clientY);
    if (!target) return;
    var key = target.closest('.kmw-key') || target.closest('.kmw-suggestion') || target.closest('.kmw-suggestion-item') || target.closest('#kmw-banner');
    if (key) {
      if (window.ishBridge && window.ishBridge.onKeyTap) {
        window.ishBridge.onKeyTap();
      }
    }
  }, { capture: true, passive: true });

  // Direct tap handler on suggestion banner options to ensure 100% reliable suggestion acceptance
  var bannerTapCandidate = null;
  document.addEventListener('touchstart', function(e) {
    if (!e.touches || e.touches.length !== 1) return;
    var touch = e.touches[0];
    var target = document.elementFromPoint(touch.clientX, touch.clientY);
    if (!target) return;
    var opt = target.closest('.kmw-suggest-option');
    if (opt) {
      bannerTapCandidate = {
        x: touch.clientX,
        y: touch.clientY,
        opt: opt,
        time: Date.now()
      };
    } else {
      bannerTapCandidate = null;
    }
  }, { capture: true, passive: true });

  document.addEventListener('touchend', function(e) {
    if (!bannerTapCandidate) return;
    var touch = (e.changedTouches && e.changedTouches.length > 0) ? e.changedTouches[0] : null;
    var start = bannerTapCandidate;
    bannerTapCandidate = null;
    if (!touch) return;
    var dx = Math.abs(touch.clientX - start.x);
    var dy = Math.abs(touch.clientY - start.y);
    var dt = Date.now() - start.time;
    // Genuine tap within 30px bounds and under 600ms
    if (dx < 30 && dy < 30 && dt < 600) {
      var opt = start.opt;
      var bs = opt && opt.suggestion;
      var suggObj = bs && bs.suggestion;
      var pc = keyman.contextManager && keyman.contextManager.predictionContext;
      if (suggObj && pc && pc.accept) {
        setTimeout(function() {
          // If Keyman already accepted within 400ms, pc.accept will safely drop this duplicate!
          if (opt && opt.suggestion && opt.suggestion.suggestion === suggObj) {
            console.log("ISH Suggestion tap fallback triggered for: " + (suggObj.displayAs || ""));
            pc.accept(suggObj);
          }
        }, 120);
      }
    }
  }, { capture: true, passive: true });

  // Direct touch interceptors for Tonal, Shift (double-tap caps lock), Backspace/Numlock reset, and Emoji keys
  document.addEventListener('touchstart', function(e) {
    if (!e.touches || e.touches.length === 0) return;
    var touch = e.touches[0];
    var target = document.elementFromPoint(touch.clientX, touch.clientY);
    if (!target) return;

    // 1. Tonal Key Interceptor
    var tonalKey = target.closest('[id$="-K_TONAL"]') || target.closest('[id*="K_TONAL"]');
    if (tonalKey) {
      e.preventDefault();
      e.stopImmediatePropagation();
      _ishTonalActive = !_ishTonalActive;
      updateTonalVisuals(_ishTonalActive);
      if (window.ishBridge && window.ishBridge.onKeyTap) {
        window.ishBridge.onKeyTap();
      }
      return;
    }

    // 2. Shift Key Interceptor for Single-Tap Uppercase & Double-Tap Caps Lock
    var shiftKey = target.closest('[id$="-K_SHIFT"]');
    if (shiftKey) {
      e.preventDefault();
      e.stopImmediatePropagation();
      var now = Date.now();
      var vkbd = (keyman && keyman.osk && keyman.osk.vkbd) ? keyman.osk.vkbd : null;

      // Check if there is an active word currently being typed before the cursor
      var bridgeCursorText = (window.ishBridge && window.ishBridge.getTextBeforeCursor) ? window.ishBridge.getTextBeforeCursor(40) : '';
      var curTokenMatch = bridgeCursorText.match(/[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$/);
      var curToken = curTokenMatch ? curTokenMatch[0] : '';
      if (!curToken && keyman && keyman.context && keyman.context.getTextBeforeCaret) {
        var kmText = keyman.context.getTextBeforeCaret() || '';
        var kmMatch = kmText.match(/[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$/);
        if (kmMatch) curToken = kmMatch[0];
      }

      var targetCase = 'title';

      if (curToken && curToken.length > 0) {
        // --- Word Casing Cycle: lower -> Title -> UPPER -> lower ---
        var curCasing = getWordCasing(curToken);

        if (_ishCapsLocked || curCasing === 'upper') {
          // In UPPER (Caps Lock) -> cycle back to lowercase
          _ishCapsLocked = false;
          _ishShiftWordActive = false;
          _ishLastShiftTapTime = 0;
          if (vkbd) {
            vkbd.lockLayer(false);
            vkbd.layerId = 'default';
          }
          if (keyman.core && keyman.core.keyboardProcessor) {
            keyman.core.keyboardProcessor.layerId = 'default';
          }
          updateShiftKeyVisual(false);
          targetCase = 'lower';
        } else if (_ishShiftWordActive || curCasing === 'title') {
          // In TitleCase -> cycle to ALL CAPS (Caps Lock)
          _ishCapsLocked = true;
          _ishShiftWordActive = false;
          _ishLastShiftTapTime = 0;
          if (vkbd) {
            vkbd.lockLayer(true);
            vkbd.layerId = 'shift';
          }
          if (keyman.core && keyman.core.keyboardProcessor) {
            keyman.core.keyboardProcessor.layerId = 'shift';
          }
          updateShiftKeyVisual(true);
          targetCase = 'upper';
        } else {
          // In lowercase -> cycle to TitleCase
          _ishCapsLocked = false;
          _ishShiftWordActive = true;
          _ishLastShiftTapTime = now;
          if (vkbd) {
            vkbd.lockLayer(false);
            vkbd.layerId = 'default';
          }
          if (keyman.core && keyman.core.keyboardProcessor) {
            keyman.core.keyboardProcessor.layerId = 'default';
          }
          updateShiftKeyVisual(false);
          targetCase = 'title';
        }

        // Transform the active word currently in the input editor
        var newWord = applyCasing(curToken, targetCase);
        if (newWord !== curToken) {
          if (window.jsInterface && window.jsInterface.insertText) {
            window.jsInterface.insertText(curToken.length, newWord, 0, false);
          }
          try {
            if (keyman && keyman.context) {
              var before = keyman.context.getTextBeforeCaret() || '';
              var after = keyman.context.getTextAfterCaret() || '';
              if (before.endsWith(curToken)) {
                var newBefore = before.slice(0, before.length - curToken.length) + newWord;
                keyman.context.setText(newBefore + after);
                keyman.context.setSelection(newBefore.length, newBefore.length);
                if (keyman.context.saveState) keyman.context.saveState();
              }
            }
          } catch(ctxErr) {}
        }
      } else {
        // --- Standard Shift / Caps Lock toggle (no active word before caret) ---
        _ishShiftWordActive = false;
        if (_ishCapsLocked) {
          // Unlock Caps Lock -> return to default lowercase
          _ishCapsLocked = false;
          _ishLastShiftTapTime = 0;
          if (vkbd) {
            vkbd.lockLayer(false);
            vkbd.layerId = 'default';
          }
          if (keyman.core && keyman.core.keyboardProcessor) {
            keyman.core.keyboardProcessor.layerId = 'default';
          }
          updateShiftKeyVisual(false);
          targetCase = 'lower';
        } else if (vkbd && vkbd.layerId === 'shift') {
          if (now - _ishLastShiftTapTime < 500) {
            // Rapid 2nd tap while on shift -> Lock to Caps!
            _ishCapsLocked = true;
            _ishLastShiftTapTime = 0;
            vkbd.lockLayer(true);
            updateShiftKeyVisual(true);
            targetCase = 'upper';
          } else {
            // Slower tap while on shift -> Return to default lowercase
            _ishCapsLocked = false;
            _ishLastShiftTapTime = 0;
            vkbd.lockLayer(false);
            vkbd.layerId = 'default';
            if (keyman.core && keyman.core.keyboardProcessor) {
              keyman.core.keyboardProcessor.layerId = 'default';
            }
            updateShiftKeyVisual(false);
            targetCase = 'lower';
          }
        } else {
          // Single tap from default -> activate shift for 1 uppercase key
          _ishCapsLocked = false;
          _ishLastShiftTapTime = now;
          if (vkbd) {
            vkbd.lockLayer(false);
            vkbd.layerId = 'shift';
          }
          if (keyman.core && keyman.core.keyboardProcessor) {
            keyman.core.keyboardProcessor.layerId = 'shift';
          }
          updateShiftKeyVisual(false);
          targetCase = 'title';
        }
      }

      // Immediately refresh suggestion banner with updated casing
      var pc = keyman.contextManager && keyman.contextManager.predictionContext;
      if (pc && pc.sendUpdateEvent) {
        setTimeout(function() {
          try {
            pc.sendUpdateEvent();
          } catch(pcErr) {}
        }, 15);
      }

      if (window.ishBridge && window.ishBridge.onKeyTap) {
        window.ishBridge.onKeyTap();
      }
      return;
    }

    // 3. Reset tonal mode if user taps any non-vowel key while tonal is active
    if (_ishTonalActive) {
      var isVowel = target.closest('.ish-vowel-key') || 
                    target.closest('[id$="-K_A"]') || target.closest('[id$="-K_E"]') || 
                    target.closest('[id$="-K_I"]') || target.closest('[id$="-K_O"]') || 
                    target.closest('[id$="-K_U"]');
      if (!isVowel) {
        _ishTonalActive = false;
        updateTonalVisuals(false);
      }
    }

    // 4. Emoji Key Interceptor
    var emojiKey = target.closest('[id*="K_EMOJI"]') || 
                   (target.innerText && (target.innerText.trim() === '😊' || target.innerText.indexOf('😊') !== -1) ? target : null);
    if (emojiKey) {
      e.preventDefault();
      e.stopImmediatePropagation();
      if (window.ishBridge && window.ishBridge.openEmojiPicker) {
        window.ishBridge.openEmojiPicker();
      }
      return;
    }
  }, { capture: true, passive: false });

  document.addEventListener('touchstart', function(e) {
    if (window.ishBridge && window.ishBridge.isTrackpadEnabled && !window.ishBridge.isTrackpadEnabled()) {
      return;
    }
    if (!e.touches || e.touches.length !== 1) return;
    var touch = e.touches[0];
    var target = document.elementFromPoint(touch.clientX, touch.clientY);
    if (!target) return;
    spacebarElem = target.closest('.kmw-spacebar') || target.closest('[id*="K_SPACE"]');
    if (!spacebarElem) return;

    scrubStartX = touch.clientX;
    scrubStartY = touch.clientY;
    lastX = touch.clientX;
    isScrubbing = false;

    clearTimeout(holdTimer);
    holdTimer = setTimeout(function() {
      // Long press threshold (220ms) triggers cursor trackpad
      isScrubbing = true;
      document.body.classList.add('ish-trackpad-scrubbing');
      if (spacebarElem) {
        spacebarElem.classList.add('ish-scrubber-active');
      }
      if (window.ishBridge && window.ishBridge.onCursorScrubberStart) {
        window.ishBridge.onCursorScrubberStart();
      }
    }, 220);
  }, { capture: true, passive: false });

  document.addEventListener('touchmove', function(e) {
    if (window.ishBridge && window.ishBridge.isTrackpadEnabled && !window.ishBridge.isTrackpadEnabled()) {
      return;
    }
    if (!spacebarElem || !e.touches || e.touches.length !== 1) return;
    var touch = e.touches[0];
    var dx = touch.clientX - lastX;
    var totalDx = touch.clientX - scrubStartX;
    var totalDy = touch.clientY - scrubStartY;

    // Fast horizontal drag enters scrubbing without waiting for timer
    if (!isScrubbing && Math.abs(totalDx) > 16 && Math.abs(totalDy) < 30) {
      clearTimeout(holdTimer);
      isScrubbing = true;
      document.body.classList.add('ish-trackpad-scrubbing');
      if (spacebarElem) {
        spacebarElem.classList.add('ish-scrubber-active');
      }
      if (window.ishBridge && window.ishBridge.onCursorScrubberStart) {
        window.ishBridge.onCursorScrubberStart();
      }
    }

    if (isScrubbing) {
      e.preventDefault();
      e.stopImmediatePropagation();

      var stepThreshold = 14; // pixels per cursor step
      if (Math.abs(dx) >= stepThreshold) {
        var steps = Math.floor(Math.abs(dx) / stepThreshold);
        var dir = dx > 0 ? 1 : -1;
        for (var i = 0; i < steps; i++) {
          if (window.ishBridge && window.ishBridge.moveCursor) {
            window.ishBridge.moveCursor(dir);
          }
        }
        lastX = lastX + (dir * steps * stepThreshold);
      }
    }
  }, { capture: true, passive: false });

  function endScrub(e) {
    clearTimeout(holdTimer);
    document.body.classList.remove('ish-trackpad-scrubbing');
    if (isScrubbing) {
      e.preventDefault();
      e.stopImmediatePropagation();
      isScrubbing = false;
      _ishScrubJustFinished = true;
      setTimeout(function() { _ishScrubJustFinished = false; }, 250);

      if (spacebarElem) {
        spacebarElem.classList.remove('ish-scrubber-active');
        spacebarElem.classList.remove('kmw-key-touched');
      }
      if (window.ishBridge && window.ishBridge.onCursorScrubberEnd) {
        window.ishBridge.onCursorScrubberEnd();
      }
    }
    // Clean up any lingering key touched states immediately
    var touched = document.querySelectorAll('.kmw-key-touched');
    for (var i = 0; i < touched.length; i++) {
      touched[i].classList.remove('kmw-key-touched');
    }
    if (keyman && keyman.osk && keyman.osk.vkbd && keyman.osk.vkbd.highlight) {
      try {
        keyman.osk.vkbd.highlight(null, false);
      } catch(err) {}
    }
    spacebarElem = null;
  }

  document.addEventListener('touchend', endScrub, { capture: true, passive: false });
  document.addEventListener('touchcancel', endScrub, { capture: true, passive: false });
}

var lastSpaceTime = 0;

/**
 * Inserts the selected string <i>s</i>
 * @param dn  Number of pre-caret code points (UTF+8 characters) to delete
 * @param s   Text to insert
 * @param dr  Number of post-caret code points to delete.  (optional)
 */
function insertText(dn, s, dr) {
  console_debug('insertText(dn='+dn+',s='+s+',dr='+dr+')');
  dr = dr || 0; // Sets a default value of zero when dr is undefined

  // Reset double-space tracker, literal override, and word-level shift cycle
  if (s !== ' ') {
    lastSpaceTime = 0;
    _ishLiteralAcceptedWord = null;
    _ishShiftWordActive = false;
  } else {
    _ishShiftWordActive = false;
  }

  // Intercept Tonal toggle if triggered via keyman rule or fallback
  var curLayer = (keyman && keyman.osk && keyman.osk.vkbd) ? keyman.osk.vkbd.layerId : 'default';
  if ((s === '^' || s === '*TONAL*') && curLayer !== 'symbols') {
    _ishTonalActive = !_ishTonalActive;
    updateTonalVisuals(_ishTonalActive);
    return;
  }

  // Intercept Emoji button
  if (s === '😊' || s === '\uD83D\uDE0A' || s === '*EMOJI*') {
    if (window.ishBridge && window.ishBridge.openEmojiPicker) {
      window.ishBridge.openEmojiPicker();
    }
    return;
  }

  // If Caps Locked, force uppercase output text
  if (_ishCapsLocked && s && typeof s === 'string') {
    s = s.toUpperCase();
  }

  // Tonal Mode processing:
  // If Tonal Mode is active:
  // - If vowel: mutate s to its circumflex tonal variant (â, ê, î, ô, û / Â, Ê, Î, Ô, Û)
  // - Always reset Tonal Mode afterwards
  if (_ishTonalActive) {
    _ishTonalActive = false;
    syncTonalAndCapsVisuals();
    if (TONAL_VOWELS[s]) {
      var origS = s;
      s = TONAL_VOWELS[s];
      if (_ishCapsLocked) {
        s = s.toUpperCase();
      }
      // Keep Keyman internal context synchronized with the transformed vowel
      try {
        if (keyman && keyman.context) {
          var before = keyman.context.getTextBeforeCaret() || '';
          var after = keyman.context.getTextAfterCaret() || '';
          if (before.endsWith(origS)) {
            var newBefore = before.slice(0, -origS.length) + s;
            keyman.context.setText(newBefore + after);
            keyman.context.setSelection(newBefore.length, newBefore.length);
          }
        }
      } catch (ctxSyncErr) {}
    }
  }

  // Shift & Caps Lock Layer Management
  var postLayer = (keyman && keyman.osk && keyman.osk.vkbd) ? keyman.osk.vkbd.layerId : 'default';
  if (_ishCapsLocked) {
    if (s && s.length === 1 && s !== ' ') {
      setTimeout(function() {
        try {
          if (keyman && keyman.osk && keyman.osk.vkbd) {
            keyman.osk.vkbd.layerId = 'shift';
            keyman.osk.vkbd.lockLayer(true);
          }
          if (keyman && keyman.core && keyman.core.keyboardProcessor) {
            keyman.core.keyboardProcessor.layerId = 'shift';
          }
          syncTonalAndCapsVisuals();
        } catch(e) {}
      }, 10);
    }
  } else if (postLayer === 'shift' && s && s.length === 1 && s !== ' ') {
    // Single tap on Shift: exactly 1 uppercase letter typed -> return to default layer!
    setTimeout(function() {
      try {
        if (keyman && keyman.osk && keyman.osk.vkbd) {
          keyman.osk.vkbd.lockLayer(false);
          keyman.osk.vkbd.layerId = 'default';
        }
        if (keyman && keyman.core && keyman.core.keyboardProcessor) {
          keyman.core.keyboardProcessor.layerId = 'default';
        }
        syncTonalAndCapsVisuals();
      } catch(e) {}
    }, 10);
  }

  // Suppress spacebar insertion immediately after cursor scrubbing
  if (s === ' ' && _ishScrubJustFinished) {
    console.log("ISH: Suppressing space insertion after cursor trackpad scrub");
    return;
  }

  var doubleTapPeriodTriggered = false;
  var spacebarAutoSplitTriggered = false;
  var punctuationTrailingSpaceConsumed = false;

  var isSpace = (s === ' ');
  var isPunctuation = (s && s.length === 1 && /[?.!,:;]/.test(s));

  // Intercept Spacebar & Punctuation for Double-space period and automatic ISH Decoupling
  if ((isSpace || isPunctuation) && dn === 0) {
    var now = Date.now();
    if (isSpace) {
      try {
        var isDoubleTapEnabled = (window.ishBridge && window.ishBridge.isDoubleTapPeriodEnabled) ? window.ishBridge.isDoubleTapPeriodEnabled() : false;
        var bridgeCursorText = (window.ishBridge && window.ishBridge.getTextBeforeCursor) ? window.ishBridge.getTextBeforeCursor(10) : '';
        if (isDoubleTapEnabled && (now - lastSpaceTime < 500) && bridgeCursorText && bridgeCursorText.endsWith(' ') && !/[.?!,:;]\s$/.test(bridgeCursorText) && !bridgeCursorText.endsWith('  ') && bridgeCursorText.trim().length > 0) {
          doubleTapPeriodTriggered = true;
          lastSpaceTime = 0;
          dn = 1;
          s = '. ';
          console.log("ISH Double-space for period triggered: replacing space with '. '");
          try {
            if (keyman && keyman.context) {
              var before = keyman.context.getTextBeforeCaret() || '';
              var after = keyman.context.getTextAfterCaret() || '';
              if (before.endsWith(' ')) {
                var newBefore = before.slice(0, -1) + '. ';
                keyman.context.setText(newBefore + after);
                keyman.context.setSelection(newBefore.length, newBefore.length);
                if (keyman.context.saveState) {
                  keyman.context.saveState();
                }
              }
            }
          } catch(ctxErr) {}
          setTimeout(function() {
            try {
              if (keyman && keyman.resetContext) {
                keyman.resetContext();
              } else if (keyman && keyman.contextManager && keyman.contextManager.resetContext) {
                keyman.contextManager.resetContext();
              }
            } catch(err) {}
          }, 10);
        } else {
          lastSpaceTime = now;
        }
      } catch(dtErr) {
        console.warn("Double tap period check error: " + dtErr);
      }
    }

    if (!doubleTapPeriodTriggered) {
      try {
        var map = loadIshFusedMap();
        var bridgeText = (window.ishBridge && window.ishBridge.getTextBeforeCursor) ? window.ishBridge.getTextBeforeCursor(100) : '';
        var textBefore = (keyman && keyman.context && keyman.context.getTextBeforeCaret) ? keyman.context.getTextBeforeCaret() : '';
        var coreContext = (keyman && keyman.core && keyman.core.languageProcessor && keyman.core.languageProcessor.contextTracker) ? keyman.core.languageProcessor.contextTracker.newest : null;
        var preInput = (coreContext && coreContext.preInput) ? coreContext.preInput.text : '';
        
        var rawText = bridgeText || textBefore || preInput || '';

        // If user typed punctuation right after a trailing space, consume the trailing space (e.g. "a ni " + "?" -> "a ni?")
        if (isPunctuation && rawText && /\s$/.test(rawText) && !/\s\s$/.test(rawText)) {
          console.log("ISH: Consuming trailing space before punctuation: '" + s + "'");
          dn = 1;
          punctuationTrailingSpaceConsumed = true;
        }

        // If rawText already ends with whitespace before this space was typed, skip consecutive space auto-split
        if (isSpace && rawText && /\s$/.test(rawText)) {
          console.log("ISH: Cursor preceded by whitespace, skipping spacebar auto-split");
        } else {
          var effectiveText = rawText.replace(/\s+$/, '');
          var m = effectiveText.match(/[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$/);
          var lastWord = m ? m[0] : '';
          if (lastWord) {
            var low = lastWord.toLowerCase();
            var target = null;
            if (_ishLiteralAcceptedWord && low === _ishLiteralAcceptedWord) {
              console.log("ISH: Bypassing auto-split for literal override: '" + lastWord + "'");
              _ishLiteralAcceptedWord = null;
              target = null;
            } else {
              if (window.ishBridge && window.ishBridge.getExactReplacement) {
                target = window.ishBridge.getExactReplacement(lastWord);
              }
              if (!target && map) {
                target = map[low];
              }
              if (!target) {
                // Dynamic suffix rules fallback
                if (low.endsWith('haiah') && low.length > 5) target = low.slice(0, -5) + ' hai ah';
                else if (low.endsWith('haiin') && low.length > 5) target = low.slice(0, -5) + ' hai in';
                else if (low.endsWith('hai') && low.length > 4 && !low.endsWith('thai') && !low.endsWith('phai')) target = low.slice(0, -3) + ' hai';
                else if (low.endsWith('tein') && low.length > 4) target = low.slice(0, -4) + ' te in';
                else if (low.endsWith('teah') && low.length > 4) target = low.slice(0, -4) + ' te ah';
                else if (low.endsWith('puiin') && low.length > 5) target = low.slice(0, -5) + ' pui in';
                else if (low.endsWith('puiah') && low.length > 5) target = low.slice(0, -5) + ' pui ah';
                else if (low.endsWith('naa') && low.length >= 4) target = low.slice(0, -1) + ' a';
                else if (low.endsWith('aa') && low.length >= 3 && low !== 'ncaa' && low !== 'caa' && low !== 'staa' && low !== 'mpaa' && low !== 'aaaa') target = low.slice(0, -1) + ' a';
              }
            }
            if (target) {
              spacebarAutoSplitTriggered = true;
              var cased = matchCase(lastWord, target);
              var trailing = isSpace ? ' ' : s;
              // If there was a trailing space before punctuation, delete lastWord + the space
              dn = (isPunctuation && rawText && /\s$/.test(rawText)) ? (lastWord.length + 1) : lastWord.length;
              s = cased + trailing;
              console.log("ISH auto-split on delimiter (" + (isSpace ? "SPACE" : trailing) + "): '" + lastWord + "' -> '" + s + "' (deleted " + dn + ")");

              // Synchronize Keyman's internal context with the decoupled word
              try {
                if (keyman && keyman.context) {
                  var before = keyman.context.getTextBeforeCaret() || '';
                  var after = keyman.context.getTextAfterCaret() || '';
                  var idx = before.lastIndexOf(lastWord);
                  if (idx !== -1) {
                    var newBefore = before.substring(0, idx) + cased + trailing;
                    keyman.context.setText(newBefore + after);
                    keyman.context.setSelection(newBefore.length, newBefore.length);
                    if (keyman.context.saveState) {
                      keyman.context.saveState();
                    }
                  }
                }
              } catch (ctxErr) {
                console.warn("Error updating Keyman context: " + ctxErr);
              }

              // Invalidate old suggestions so banner immediately displays next-word predictions
              setTimeout(function() {
                try {
                  if (keyman && keyman.resetContext) {
                    keyman.resetContext();
                  } else if (keyman && keyman.contextManager && keyman.contextManager.resetContext) {
                    keyman.contextManager.resetContext();
                  }
                } catch(err) {}
              }, 10);
            }
          }
        }

        // If trailing space was consumed before punctuation without auto-split, synchronize Keyman context
        if (punctuationTrailingSpaceConsumed && !spacebarAutoSplitTriggered) {
          try {
            if (keyman && keyman.context) {
              var before = keyman.context.getTextBeforeCaret() || '';
              var after = keyman.context.getTextAfterCaret() || '';
              if (before.endsWith(' ')) {
                var newBefore = before.slice(0, -1) + s;
                keyman.context.setText(newBefore + after);
                keyman.context.setSelection(newBefore.length, newBefore.length);
                if (keyman.context.saveState) {
                  keyman.context.saveState();
                }
              }
            }
          } catch(ctxErr) {}
          setTimeout(function() {
            try {
              if (keyman && keyman.resetContext) {
                keyman.resetContext();
              } else if (keyman && keyman.contextManager && keyman.contextManager.resetContext) {
                keyman.contextManager.resetContext();
              }
            } catch(err) {}
          }, 10);
        }
      } catch (e) {
        console.warn("ISH auto-split error: " + e);
      }
    }
  }

  // Boundary guard for suggestion insertions and replacements:
  // When inserting text with a deletion (dn > 0 && s.length > 0), prevent Keyman
  // from backtracking across word or whitespace boundaries into preceding words.
  if (dn > 0 && s && s.length > 0 && !doubleTapPeriodTriggered && !spacebarAutoSplitTriggered && !punctuationTrailingSpaceConsumed) {
    try {
      var bridgeCursorText = (window.ishBridge && window.ishBridge.getTextBeforeCursor) ? window.ishBridge.getTextBeforeCursor(100) : '';
      if (bridgeCursorText) {
        var wordMatch = bridgeCursorText.match(/[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+(-[a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)*$/);
        var curWord = wordMatch ? wordMatch[0] : '';
        if (!curWord) {
          // Cursor is at a boundary (after space or punctuation). Next-word suggestion must not delete backwards.
          // Dead keys / circumflex markers (^ / \u0302) must be deleted when transformed!
          if (bridgeCursorText.endsWith('^') || bridgeCursorText.endsWith('\u0302')) {
            // Allow dead key replacement deletion
          } else if (dn > 0) {
            console.log("ISH Guard: suggestion tapped at boundary without active word token, clamping dn from " + dn + " to 0");
            dn = 0;
          }
        } else {
          // If inserting a replacement word when an active word token is at the cursor,
          // ensure the entire token is deleted rather than leaving partial prefix fragments.
          console.log("ISH Guard: token '" + curWord + "' replaced by '" + s + "', clamping dn from " + dn + " to " + curWord.length);
          dn = curWord.length;
        }
      }
    } catch(guardErr) {
      console.warn("ISH insertText guard error: " + guardErr);
    }
  }

  window.jsInterface.insertText(dn, s, dr, executingHardwareKeystroke);

  try {
    if (keyman && keyman.context && keyman.context.saveState) {
      keyman.context.saveState();
    }
  } catch(ssErr) {}

  // If committed token is a sentence delimiter (. ! ? \n) or space, notify sentence boundary for corpus recording
  if (window.ishBridge && window.ishBridge.onSentenceBoundary) {
    try {
      if (s === '.' || s === '?' || s === '!' || s === '\n' || s === ' ') {
        var bridgeFullText = (window.ishBridge && window.ishBridge.getTextBeforeCursor) ? window.ishBridge.getTextBeforeCursor(1000) : '';
        if (bridgeFullText) {
          window.ishBridge.onSentenceBoundary(bridgeFullText + (s === ' ' ? '' : s));
        }
      }
    } catch(err) {
      console.warn("onSentenceBoundary error: " + err);
    }
  }

  // Live real-time bigram learning on space
  if (s === ' ' && window.ishBridge && window.ishBridge.onWordTransition) {
    try {
      var fullTxt = (window.ishBridge && window.ishBridge.getTextBeforeCursor) ? window.ishBridge.getTextBeforeCursor(200) : '';
      if (fullTxt) {
        var pairMatch = fullTxt.trim().match(/([a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)\s+([a-zA-Z\u00C0-\u024F\u1E6C\u1E6D]+)$/);
        if (pairMatch && pairMatch[1] && pairMatch[2]) {
          window.ishBridge.onWordTransition(pairMatch[1], pairMatch[2]);
        }
      }
    } catch(tErr) {}
  }
}

function deregisterModel(modelID) {
  keyman.removeModel(modelID);
}

function enableSuggestions(model, mayPredict, mayCorrect) {
  // Set the options first so that KMW's ModelManager can properly handle model enablement states
  // the moment we actually register the new model.
  keyman.core.languageProcessor.mayPredict = mayPredict;
  keyman.core.languageProcessor.mayCorrect = mayCorrect;

  registerModel(model);
}

function setBannerOptions(mayPredict) {
  keyman.core.languageProcessor.mayPredict = mayPredict;
}

function registerModel(model) {
  console.log('registerModel called for: ' + (model ? model.id : 'null'));
  if (model && model.path) {
    var loadedCode = null;

    // Check the in-memory cache first — avoids re-reading the 11.7MB asset on every
    // registerLexicalModel call triggered by onKeyboardLoaded / onStartInput.
    if (model.id && _ishModelCodeCache[model.id]) {
      loadedCode = _ishModelCodeCache[model.id];
      console.log('registerModel: using cached model code for ' + model.id + ' (' + loadedCode.length + ' chars)');
    } else if (window.ishBridge && window.ishBridge.getModelCode) {
      try {
        loadedCode = window.ishBridge.getModelCode(model.id);
        if (loadedCode && loadedCode.length > 0) {
          console.log('Got model code from ishBridge, length=' + loadedCode.length);
          // Cache it so subsequent calls skip the 11.7MB asset read
          if (model.id) {
            _ishModelCodeCache[model.id] = loadedCode;
          }
        }
      } catch (e) {
        console.error('ishBridge error: ' + e);
      }
    }

    if (loadedCode) {
      var codeModel = {
        id: model.id,
        languages: model.languages,
        code: loadedCode
      };
      keyman.addModel(codeModel);
      return;
    }

    try {
      var xhr = new XMLHttpRequest();
      xhr.open('GET', model.path, true);
      xhr.onload = function() {
        if (xhr.status === 200 || xhr.status === 0) {
          console.log('Loaded model code via XHR, length=' + xhr.responseText.length);
          var codeModel = {
            id: model.id,
            languages: model.languages,
            code: xhr.responseText
          };
          // Cache XHR-loaded code too
          if (model.id) {
            _ishModelCodeCache[model.id] = xhr.responseText;
          }
          keyman.addModel(codeModel);
        } else {
          console.warn('XHR status ' + xhr.status + ', fallback to path');
          keyman.addModel(model);
        }
      };
      xhr.onerror = function(e) {
        console.warn('XHR error, fallback to path: ' + e);
        keyman.addModel(model);
      };
      xhr.send();
      return;
    } catch(e) {
      console.warn('XHR exception: ' + e);
    }
  }
  keyman.addModel(model);
}

function resetContext() {
  keyman.resetContext();
}

// Tell KMW to switch to "numeric" layer
function setNumericLayer() {
  if (keyman && keyman.core && keyman.core.activeKeyboard) {
    keyman.setNumericLayer();
  }
}

function updateKMText(k) {
  var text = (k == undefined) || !k.text ? '' : k.text;

  console_debug('updateKMText(text=' + text + ') with: \n' + build_context_string(keyman.context));

  if(!text || text != keyman.context.getText()) {
    keyman.context.setText(text);
    keyman.resetContext();


    console_debug('result: \n' + build_context_string(keyman.context));
  } else {
    console_debug('context unchanged');
  }
}

function console_debug(s) {
  if(_debug) {
    console.debug(s);
  }
}

function build_context_string(context) {
  // Sadly, ES6-style "template strings" - strings with backticks - require Chrome 41+.
  return 'preCaret: `' + context.getTextBeforeCaret() + '`\n' +
    'selected: `' + context.getSelectedText() + '`\n' +
    'postCaret: `' + context.getTextAfterCaret() + '`';
}

function updateKMSelectionRange(start, end) {
  var context = keyman.context;

  console_debug('updateKMSelectionRange(' + start + ', ' + end + ') with: \n' + build_context_string(context));

  if(start > end) {
    var e0 = end;
    end = start;
    start = e0;
  }

  if(context.selStart != start || context.selEnd != end) {
    keyman.context.setSelection(start, end);
    keyman.resetContext();

    console_debug('result:\n' + build_context_string(context));
  } else {
    console.debug('range unchanged');
  }
}

var lastKeyTip = null;

function signalHelpBubbleDismissal() {
  fragmentToggle = (fragmentToggle + 1) % 100;
  window.location.hash = 'helpBubbleDismissed-'+fragmentToggle;
}

function suggestionPopup(obj,custom,x,y,w,h) {
  if(obj != null) {
    var s = JSON.stringify(obj);

    fragmentToggle=(fragmentToggle+1) % 100;
    var hash = 'suggestPopup-' + fragmentToggle;

    hash = hash + '&x=' + encodeURIComponent(x) + '&y=' + encodeURIComponent(y);
    hash = hash + '&w=' + encodeURIComponent(w) + '&h=' + encodeURIComponent(h);
    hash = hash + '&suggestion=' + encodeURIComponent(s) + '&custom=' + encodeURIComponent(custom);

    window.location.hash = hash;
  }
}

// legacy function name for globe key down
function showMenu() {
  fragmentToggle = (fragmentToggle + 1) % 100;
  var hash = 'globeKeyAction&fragmentToggle=' + fragmentToggle + '&keydown=true';
  window.location.hash = hash;
}

// legacy function name for globe key up
function menuKeyUp() {
  fragmentToggle = (fragmentToggle + 1) % 100;
  var hash = 'globeKeyAction&fragmentToggle=' + fragmentToggle + '&keydown=false';
  window.location.hash = hash;
}

// The keyboard-picker displayed via Android longpress disrupts Web-side
// gesture-handling; this function helps force-clear the globe key's highlighting.
function clearGlobeHighlight() {
  if(keyman.osk && keyman.osk.vkbd && keyman.osk.vkbd.currentLayer.globeKey) {
    keyman.osk.vkbd.currentLayer.globeKey.highlight(false)
  }
}

function hideKeyboard() {
  fragmentToggle = (fragmentToggle + 1) % 100;
  window.location.hash = 'hideKeyboard' + fragmentToggle;
}

function showKeyboard() {
  // Refresh KMW OSK
  keyman.refreshOskLayout();
}

// Cannot make it explicitly async / await on API 21.
function executeHardwareKeystroke(code, shift, lstates, eventModifiers) {
  console_debug('executeHardwareKeystroke(code='+code+',shift='+shift+',lstates='+lstates+',eventModifiers='+eventModifiers+')');

  executingHardwareKeystroke = true;

  // Would be cleaner if we could async / await here, which would give us a simple try-catch implementation.
  var promise = keyman.hardKeyboard.raiseKeyEvent(code, shift, lstates);
  promise.then(function (result) {
    if(result) { // false if matched, true if not
      // KMW didn't process the key, so have the Android app dispatch the key with the original event modifiers
      window.jsInterface.dispatchKey(code, eventModifiers);
      executingHardwareKeystroke = false;
    }
  }).catch(function (e) {
    window.console.log('executeHardwareKeystroke exception: '+e);
    executingHardwareKeystroke = false;
  });
}

function popupVisible(value) {
  keyman.popupVisible(value);
}

function toHex(theString) {
  if (typeof theString != 'string') {
    return '';
  }
  var hexString = '';
  for (var i=0; i < theString.length; i++) {
    var theHex = theString.charCodeAt(i).toString(16).toUpperCase();
    while (theHex.length < 4) {
      theHex = '0' + theHex;
    }
    theHex = '\\u' + theHex;
    hexString += theHex;
  }
  return hexString;
}
