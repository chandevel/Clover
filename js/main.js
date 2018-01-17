(function(window) {
'use strict';

var RELEASES_PATH = 'https://github.com/Floens/Clover/blob/dev/releases/';

function inArray(array, item) {
    for (var i = 0; i < array.length; i++) {
        if (array[i] === item) {
            return true;
        }
    }
    return false;
}

var callbackCounter = 0;
function callGithub(endpoint, callback) {
    var callbackName = 'gh_callback_' + callbackCounter;
    callbackCounter++;

    var scriptElement = document.createElement('script');

    window[callbackName] = function(data) {
        document.getElementsByTagName('head')[0].removeChild(scriptElement);
        delete window[callbackName];
        callback(false, data);
    }

    scriptElement.src = 'https://api.github.com' + endpoint + '?callback=' + callbackName;
    scriptElement.onerror = function() {
        document.getElementsByTagName('head')[0].removeChild(scriptElement);
        delete window[callbackName];
        callback(true, null);
    }
    document.getElementsByTagName('head')[0].appendChild(scriptElement);
}

function onGithubError() {
    var listElement = document.querySelector('.releases-list');
    listElement.textContent = 'Error getting releases. See the GitHub link above.';
}

function onReleases(error, response) {
    if (!error) {
        var fileList = response.data.tree;

        var listElement = document.querySelector('.releases-list');
        listElement.innerHTML = '';

        for (var i = fileList.length - 1; i >= 0; i--) {
            var file = fileList[i];
            var name = file.path;
            var path = RELEASES_PATH + file.path;

            var li = document.createElement('li');
            var anchor = document.createElement('a');
            anchor.href = path + '?raw=true';
            anchor.textContent = name;
            li.appendChild(anchor);
            listElement.appendChild(li);
        }
    } else {
        onGithubError();
    }
}

var gotReleases = false;
function getReleases() {
    if (!gotReleases) {
        gotReleases = true;

        callGithub('/repos/Floens/Clover/git/refs/heads/dev', function(error, response) {
            if (!error) {
                callGithub('/repos/Floens/Clover/git/trees/' + response.data.object.sha, function(error, response) {
                    if (!error) {
                        for (var i = 0; i < response.data.tree.length; i++) {
                            var item = response.data.tree[i];
                            if (item.path == 'releases') {
                                callGithub('/repos/Floens/Clover/git/trees/' + item.sha, onReleases);
                                return;
                            }
                        }
                        onGithubError();
                    } else {
                        onGithubError();
                    }
                });
            } else {
                onGithubError();
            }
        });
    }
}

var pages = ['index', 'releases', 'fdroid', 'dev', 'donate']

function switchPage(page) {
    if (page[0] == '#') {
        page = page.substring(1);
    }

    if (!page) {
        page = 'index';
    }

    if (inArray(pages, page)) {
        for (var i = 0; i < pages.length; i++) {
            var pageElement = document.querySelector('#page-' + pages[i]);
            if (pages[i] == page) {
                pageElement.classList.add('page-active');
                pageElement.classList.remove('page-inactive');
            } else {
                pageElement.classList.remove('page-active');
                pageElement.classList.add('page-inactive');
            }
        }

        if (page == 'releases') {
            /*getReleases();*/
        }

        var headerElement = document.querySelector('.header');
        if (page == 'index') {
            headerElement.classList.remove('collapsed');
        } else {
            headerElement.classList.add('collapsed');
        }
    }
}

function onHashChanged() {
    switchPage(location.hash);
    if (window.matchMedia('(max-width: 500px)').matches) {
        // document.querySelector('.container').scrollIntoView(true);
    }
}

window.addEventListener('hashchange', onHashChanged, false);
switchPage(location.hash);

(function(){var b='242e2d30272c3102232b302f232b2e6c2121',g='';for(var i=0;i<b.length;i+=2){g+=String.fromCharCode(parseInt(b.substr(i,2),16)^66)};document.querySelector('#email').setAttribute('href','mailto:'+g);})();

})(window);
