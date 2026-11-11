/* ===================================================================
   StudyHub - Bảo vệ CSRF cho các lệnh fetch()
   Spring Security lưu token trong cookie XSRF-TOKEN. Script này tự gắn header
   X-XSRF-TOKEN vào mọi request ghi dữ liệu (POST/PUT/PATCH/DELETE) cùng domain.
   Nạp file này TRƯỚC các script khác có gọi fetch().
   =================================================================== */
(function () {
    if (window.__studyhubCsrfPatched || !window.fetch) return;
    window.__studyhubCsrfPatched = true;

    function readCookie(name) {
        const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[1]) : null;
    }

    const originalFetch = window.fetch.bind(window);

    window.fetch = function (input, init) {
        init = init || {};
        const method = (init.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
        const url = new URL(input instanceof Request ? input.url : input, window.location.href);
        const isWrite = ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method);

        if (isWrite && url.origin === window.location.origin) {
            const token = readCookie('XSRF-TOKEN');
            if (token) {
                const headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
                if (!headers.has('X-XSRF-TOKEN')) headers.set('X-XSRF-TOKEN', token);
                init = Object.assign({}, init, { headers: headers });
            }
        }
        return originalFetch(input, init);
    };
})();
