/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023 Matous Kucera
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

const themeKey = "_theme";

const toggleTheme = () => setTheme(getTheme() === "light" ? "dark" : "light");
const getTheme = () => localStorage.getItem(themeKey) || (window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark");
const setTheme = (theme) => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem(themeKey, theme);
};

window.addEventListener("load", () => document.documentElement.setAttribute("data-theme", getTheme()));

// a map of "<namespace>" to "<namespace badge color>"
let colors = {};
// a list of { "<namespace>": <mapping string or null> }
let classIndex = [];

const updateClassIndex = (indexString) => {
    const header = [];
    for (const line of indexString.match(/[^\r\n]+/g)) {
        if (header.length === 0) {
            for (const col of line.split("\t")) {
                const colParts = col.split(":", 2);

                header.push(colParts[0]);
                colors[colParts[0]] = colParts[1];
            }
        } else {
            const obj = {};

            for (const [index, col] of line.split("\t", header.length).entries()) {
                obj[header[index]] = col || null;
            }

            classIndex.push(obj);
        }
    }
};

const search = (baseUrl, query) => {
    query = query.trim();
    const resultsBox = document.getElementById("search-results-box");

    let results = [];
    if (query) {
        let targetedNamespace = null;

        let newQuery = "";
        for (const option of query.split(" ")) {
            const optionParts = option.split(":", 2);
            if (optionParts.length === 2) {
                switch (optionParts[0]) {
                    case "namespace":
                    case "ns":
                        targetedNamespace = optionParts[1].toLowerCase();
                        break;
                }
            } else {
                newQuery = newQuery + option;
            }
        }

        newQuery = newQuery.replaceAll(".", "/");

        for (const klass of classIndex) {
            for (const ns in klass) {
                const klassName = klass[ns];

                // limit to 5 results
                if (results.length <= 5 && klassName && klassName.includes(newQuery) && (!targetedNamespace || targetedNamespace === ns.toLowerCase())) {
                    const elem = document.createElement("div");
                    elem.classList.add("search-result");
                    elem.addEventListener("click", () => {
                        window.location.pathname = `${baseUrl}/${Object.values(klass).find((e) => e != null)}.html`;
                    });

                    const lastSlashIndex = klassName.lastIndexOf("/");
                    const simpleKlassName = klassName.substring(lastSlashIndex + 1);
                    const klassPackage = klassName.substring(0, lastSlashIndex).replaceAll("/", ".");

                    const title = document.createElement("p");
                    title.classList.add("search-result-title");
                    title.innerText = simpleKlassName;
                    elem.appendChild(title);

                    const subtitle0 = document.createElement("p");
                    subtitle0.classList.add("search-result-subtitle");
                    subtitle0.innerText = `package: ${klassPackage}`;
                    elem.appendChild(subtitle0);

                    const subtitle1 = document.createElement("p");
                    subtitle1.classList.add("search-result-subtitle");
                    subtitle1.innerHTML = `namespace: <span style="color:${colors[ns]}">${ns}</span>`;
                    elem.appendChild(subtitle1);

                    results.push(elem);
                }
            }
        }
    }

    resultsBox.replaceChildren(...results);
};
