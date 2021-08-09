const host = document.location.host//document.location.origin;

$(function () {
    $("#dialog").dialog({
        autoOpen: false,
        modal: true,
        buttons: {
            Ok: function () {
                $(this).dialog("close");
            }
        }
    });
});

function openNav() {
    document.getElementById("mySidebar").style.width = "300px";
}

function closeNav() {
    document.getElementById("mySidebar").style.width = "0px";
}

let json;

let socket = new WebSocket(`ws://${host}/`);

/*const req = new XMLHttpRequest();
req.open("GET", `${host}/` + localStorage.getItem("file") + "/" + localStorage.getItem("file"), true);
req.onreadystatechange = function () {
    if (req.readyState === XMLHttpRequest.DONE && req.status === 200) {
        const response = removeGraphName(req.response);
        start(response);
    }
}
req.send(null);

const methods = new XMLHttpRequest();
methods.open("GET", `${host}/` + localStorage.getItem("file") + "/" + localStorage.getItem("file") + "-all", true);
methods.onreadystatechange = function () {
    if (methods.readyState === XMLHttpRequest.DONE && methods.status === 200) {
        let sidebar = document.querySelector('.sidebar');
        JSON.parse(methods.response).methods.forEach(method => {
            let a = document.createElement("a");
            a.setAttribute("class", "method-link");
            a.setAttribute("onclick", "anotherMethod(\'" + method + "\',\'" + localStorage.getItem("file") + "\');");
            a.setAttribute("href", "#");
            a.innerHTML = method.replace("<", "").replace(">", "");
            sidebar.appendChild(a);
        });
    }
}
methods.send(null);

let graphviz;

function start(j) {
    json = j;
    document.querySelector('.spinner').style.display = "none";

    let width = window.innerWidth,
        height = window.innerHeight - $('.logo').height() - 70;
    console.log(height);

    function transitionFactory() {
        return d3.transition("main").ease(d3.easeExpInOut).duration(1000);
    }

    graphviz = d3.select(".graph-pane")
        .graphviz()
        .attributer(attributer)
        .transition(transitionFactory)
        .tweenShapes(true);


    graphviz.renderDot(json);

    function attributer(datum) {
        let selection = d3.select(this);
        if (datum.tag === "svg") {
            //var width = d3.select('.graph-pane').node().clientWidth;
            //var height = d3.select('.graph-pane').node().clientHeight;
            const unit = 'px';
            selection
                .attr("width", width + unit)
                .attr("height", height + unit);
            datum.attributes.width = width + unit;
            datum.attributes.height = height + unit;
        }
    }

    d3.select(document).on("click", function () {
        console.log(d3.event.target);
        if (d3.event.target.innerText !== "☰") closeNav();
        document.getElementById("context-menu").classList.remove("active");
    })

    d3.select(document).on("contextmenu", function () {
        console.log(d3.event.target);
        d3.event.preventDefault();
        if (d3.event.target.tagName === "text") {
            let contextElement = document.getElementById("context-menu");
            contextElement.style.top = d3.event.offsetY + $('.logo').height() + 70 + "px";
            contextElement.style.left = d3.event.offsetX + "px";
            contextElement.classList.add("active");
            Array.from(document.getElementsByClassName("item")).filter(it => {
                return it.innerText === "Expand"
            })[0].setAttribute("onClick",
                "expand(\'"
                + localStorage.getItem("file")
                + "\', \'"
                + localStorage.getItem("method")
                + "\', \'"
                + d3.event.target.textContent
                    .replaceAll("%", "%25")
                    .replaceAll("/", "%5C")
                    .replaceAll("=", "%3D")
                    .replaceAll(":", "%3A")
                    .replaceAll("    ", "")
                    .replaceAll("\"", "\\\"") + "\')");
        }

    });
}

function expand(jar, method, subMethod) {
    const expandReq = new XMLHttpRequest();
    expandReq.onreadystatechange = function () {
        if (expandReq.readyState === XMLHttpRequest.DONE && expandReq.status === 200) {
            document.querySelector("body").style.pointerEvents = "all"
            if (expandReq.response.includes("Can't expand")) {
                afterWaitingWithDialog(expandReq.response)
            } else if (expandReq.response.includes("Has already been expanded")) {
                afterWaitingWithDialog(expandReq.response)
            } else {
                const response = removeGraphName(expandReq.response);
                document.querySelector('.spinner').style.display = "none";
                document.querySelector('.graph-pane').style.display = "block";
                graphviz.renderDot(response);
            }
        }
    }
    expandReq.open("GET", `${host}/` + jar + "/" + method + "/" + subMethod, true);
    document.querySelector("body").style.pointerEvents = "none";
    document.querySelector('.spinner').style.display = "block";
    document.querySelector('.graph-pane').style.display = "none";
    expandReq.send(null)
}

function anotherMethod(method, jar) {
    let elem = document.querySelector('.graph-pane').querySelector("svg");
    elem.parentNode.removeChild(elem);
    document.querySelector('.spinner').style.display = "block";

    req.open("GET", `${host}/` + jar + "/" + method, true);
    req.send(null);
}

function afterWaitingWithDialog(text) {
    document.querySelector('.spinner').style.display = "none";
    document.querySelector('.graph-pane').style.display = "block";
    document.getElementById("dialog").style.display = "block";
    document.getElementById("dialog").innerText = text;
    $("#dialog").dialog("open");
}

function removeGraphName(graph) {
    let resp = graph.split(" ");
    for (const [i, s] of resp.entries()) {
        if (s === "{") {
            localStorage.setItem("method", resp.slice(1, i).join(" "))
            for (let ii = 1; ii < i; ii++) {
                resp[ii] = ""
            }
            break;
        }
    }
    resp = resp.join(" ")
    return resp;
}*/
