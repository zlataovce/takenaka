/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023-2024 Matous Kucera
 *
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Schedules a callback to be executed when the DOM is fully loaded.
const scheduleOnDOMLoad = (callback) => {
    if (document.readyState === "loading") {
        // If the document is still loading, add an event listener for DOMContentLoaded.
        document.addEventListener("DOMContentLoaded", callback);
    } else {
        // If the document is already loaded, execute the callback immediately.
        callback();
    }
};

// Key used to store the theme in local storage.
const themeKey = "_theme";

// Toggles the theme between "light" and "dark".
const toggleTheme = () => setTheme(getTheme() === "light" ? "dark" : "light");

// Gets the current theme from local storage or defaults to the preferred color scheme of the user's system.
const getTheme = () => localStorage.getItem(themeKey) || (window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark");

// Sets the current theme in local storage and updates the data-theme attribute of the document element.
const setTheme = (theme) => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem(themeKey, theme);
};

// Sets the initial theme when the DOM is loaded.
scheduleOnDOMLoad(() => document.documentElement.setAttribute("data-theme", getTheme()));

// Object to store colors associated with namespaces.
let colors = {};
// Array to store the class index data.
let classIndex = [];
// Array to store the member index data.
let memberIndex = [];

// Promises used to signal when the class and member indices have been loaded.
let resolveClassIndexPromise;
let resolveMemberIndexPromise;
// Promise that resolves when both class and member indices are loaded.
const initialIndexLoadPromise = Promise.all([
    new Promise((resolve) => (resolveClassIndexPromise = resolve)),
    new Promise((resolve) => (resolveMemberIndexPromise = resolve))
]);

// Parses the index data string and populates the target array.
const parseIndex = (indexString, targetArray) => {
    // Replace common namespace prefixes with their full names.
    indexString = indexString.replaceAll("%nm", "net/minecraft").replaceAll("%cm", "com/mojang");

    const header = [];
    let firstLine = true;
    // Iterate over each line of the index string.
    for (const line of indexString.match(/[^\r\n]+/g)) {
        if (firstLine) {
            // The first line contains the header information.
            firstLine = false;
            for (const col of line.split("\t")) {
                // Split each column by ":" to separate namespace and color.
                const colParts = col.split(":", 2);
                const nsName = colParts[0];
                header.push(nsName);
                if (colParts.length > 1) {
                    // Store the color associated with the namespace.
                    colors[nsName] = colParts[1];
                }
            }
        } else {
            // Subsequent lines contain the actual index data.
            const obj = {};
            const cols = line.split("\t");
            for (let i = 0; i < header.length; i++) {
                // Populate an object with the data from each column, using the header as keys.
                obj[header[i]] = cols[i] || null;
            }
            targetArray.push(obj);
        }
    }
};

// Updates the class index array with the parsed data.
const updateClassIndex = (indexString) => {
    parseIndex(indexString, classIndex);
    resolveClassIndexPromise();
};

// Updates the member index array with the parsed data.
const updateMemberIndex = (indexString) => {
    parseIndex(indexString, memberIndex);
    resolveMemberIndexPromise();
};

// Loads the class and member index data from external JavaScript files.
if (window.root) {
    const classIndexScript = document.createElement("script");
    classIndexScript.async = true;
    classIndexScript.src = `${window.root}class-index.js`;
    document.head.appendChild(classIndexScript);

    const memberIndexScript = document.createElement("script");
    memberIndexScript.async = true;
    memberIndexScript.src = `${window.root}member-index.js`;
    document.head.appendChild(memberIndexScript);
}

// Key used to store the selected search namespaces in local storage.
const searchNamespacesKey = "_namespaces";

// Gets the stored search namespaces from local storage.
const getStoredSearchNamespaces = () => {
    const nsString = localStorage.getItem(searchNamespacesKey);

    return nsString ? nsString.split(",") : (nsString != null ? [] : null);
};
// Array to store the currently selected search namespaces.
let searchNamespaces = getStoredSearchNamespaces();

// Updates the stored search namespaces in local storage.
const updateSearchNamespaces = (newNamespaces) => {
    searchNamespaces = newNamespaces;
    localStorage.setItem(searchNamespacesKey, newNamespaces.join(","));
};

// Searches the class and member indices for the given query.
const search = (query) => {
    const resultsBox = document.getElementById("search-results-box");

    // Normalize the query by replacing "." with "/" and converting to lowercase.
    query = query.replaceAll(".", "/").toLowerCase().trim();
    if (!query) {
        // If the query is empty, clear the results box.
        resultsBox.replaceChildren();
        return;
    }

    const classResults = [];
    const memberResults = [];
    const hasPackage = query.includes("/");
    const hasArgs = query.includes("(");

    classLoop:
    for (const klass of classIndex) {
        for (const ns in klass) {
            // Skip namespaces that are not selected for searching.
            if (!searchNamespaces.includes(ns)) continue;

            const klassName = klass[ns];
            if (klassName) {
                const klassNameLower = klassName.toLowerCase();
                if (!klassNameLower.includes(query)) continue;

                const lastSlashIndex = klassName.lastIndexOf("/");
                const simpleName = lastSlashIndex !== -1 ? klassName.substring(lastSlashIndex + 1) : klassName;

                if (!hasPackage && !simpleName.toLowerCase().includes(query)) continue;

                classResults.push({
                    type: 'class',
                    data: klass,
                    ns: ns,
                    simpleName: simpleName,
                    packageName: lastSlashIndex !== -1 ? klassName.substring(0, lastSlashIndex).replaceAll("/", ".") : null,
                    similarity: (hasPackage ? klassName.length : simpleName.length) - query.length
                });
                continue classLoop;
            }
        }
    }

    memberLoop:
    for (const member of memberIndex) {
        for (const ns in member) {
            // Skip the "Link" namespace and namespaces that are not selected for searching.
            if (ns === "Link" || !searchNamespaces.includes(ns)) continue;

            const memberName = member[ns];
            if (memberName) {
                const memberNameLower = memberName.toLowerCase();
                if ((hasArgs && !memberName.includes("(")) || !memberNameLower.includes(query)) continue;

                const linkParts = member.Link.split('#');
                const classFriendlyName = linkParts[0];
                const memberAnchor = linkParts[1];
                const isMethod = memberAnchor.includes("(");

                let simpleName = memberName;
                if (isMethod) {
                    const parenIndex = simpleName.indexOf('(');
                    if (parenIndex !== -1) simpleName = simpleName.substring(0, parenIndex);
                }

                memberResults.push({
                    type: isMethod ? 'method' : 'field',
                    data: member,
                    ns: ns,
                    simpleName: simpleName,
                    fullMemberName: memberName,
                    classFriendlyName: classFriendlyName,
                    similarity: memberName.length - query.length
                });
                continue memberLoop;
            }
        }
    }


    // Combine class and member results, then sort them by similarity.
    const combinedResults = [...classResults, ...memberResults];
    combinedResults.sort((a, b) => {

        // If the similarity is the same, prioritize classes.
        if (a.similarity === b.similarity) {
            if (a.type === 'class' && b.type !== 'class') return -1;
            if (a.type !== 'class' && b.type === 'class') return 1;
        }
        return a.similarity - b.similarity;
    });


    // Display the search results in the results box.
    resultsBox.replaceChildren(...(
        combinedResults.slice(0, Math.min(combinedResults.length, 50 + 1 )).map((r) => {
            const resultWrap = document.createElement("a");

            let linkTargetBase;
            let linkAnchor = '';
            if (r.type === 'class') {

                linkTargetBase = Object.values(r.data).find(e => e != null);
            } else {
                const linkParts = r.data.Link.split('#');
                linkTargetBase = linkParts[0];
                linkAnchor = linkParts.length > 1 ? `#${linkParts[1]}` : '';
            }

            resultWrap.href = `${window.root}${linkTargetBase}.html${linkAnchor}`;
            resultWrap.style.textDecoration = "none";

            const resultElem = document.createElement("div");
            resultElem.classList.add("search-result");

            const title = document.createElement("p");
            title.classList.add("search-result-title");
            title.innerText = r.simpleName;
            if (r.type === 'method') {
                 const sig = r.fullMemberName.substring(r.simpleName.length);
                 const sigSpan = document.createElement("span");
                 sigSpan.style.opacity = "0.7";
                 sigSpan.innerText = sig;
                 title.appendChild(sigSpan);
            }
            resultElem.appendChild(title);

            const subtitleText = r.type === 'class'
                ? (r.packageName ? `package: ${r.packageName}` : null)
                : `in class: ${r.classFriendlyName.replaceAll("/", ".")}`;

            if (subtitleText) {
                const subtitle = document.createElement("p");
                subtitle.classList.add("search-result-subtitle");
                subtitle.innerText = subtitleText;
                subtitle.innerHTML = subtitle.innerHTML.replaceAll(".", ".<wbr>");
                resultElem.appendChild(subtitle);
            }

            const nsSubtitle = document.createElement("p");
            nsSubtitle.classList.add("search-result-subtitle");
            nsSubtitle.appendChild(document.createTextNode("namespace: "));

            const nsSubtitleBadge = document.createElement("span");
            nsSubtitleBadge.classList.add("search-badge-text");
            nsSubtitleBadge.style.color = colors[r.ns];
            nsSubtitleBadge.innerText = r.ns;

            nsSubtitle.appendChild(nsSubtitleBadge);
            resultElem.appendChild(nsSubtitle);
            resultWrap.appendChild(resultElem);
            return resultWrap;
        })
    ));
};

// Toggles the visibility of the options box.
const toggleOptions = () => {
    const optionBox = document.getElementById("option-box");
    optionBox.style.display = optionBox.style.display === "grid" ? "none" : "grid";
};

// Updates the options box with the available namespaces and their associated colors.
const updateOptions = () => {
    const searchInput = document.getElementById("search-input");
    const optionBox = document.getElementById("option-box");

    if (!searchNamespaces) {
        searchNamespaces = Object.keys(colors);
    }
    optionBox.replaceChildren(...(
        Object.entries(colors).map(([ns, color]) => {
            const labelTarget = `checkbox-${ns.toLowerCase()}`;

            const checkboxWrap = document.createElement("div");
            checkboxWrap.style.display = "flex";

            const inputElem = document.createElement("input");
            inputElem.type = "checkbox";
            inputElem.id = labelTarget;
            inputElem.checked = searchNamespaces.includes(ns);
            inputElem.addEventListener("change", () => {
                updateSearchNamespaces(inputElem.checked ? [...searchNamespaces, ns] : searchNamespaces.filter((e) => e !== ns));

                searchInput.dispatchEvent(new Event("input"));
            });

            checkboxWrap.appendChild(inputElem);

            const labelElem = document.createElement("label");
            labelElem.htmlFor = labelTarget;
            labelElem.style.color = color;
            labelElem.classList.add("search-badge-text");
            labelElem.appendChild(document.createTextNode(ns));

            checkboxWrap.appendChild(labelElem);

            return checkboxWrap;
        })
    ));
};