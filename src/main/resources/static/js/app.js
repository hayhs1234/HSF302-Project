// StudyHub JavaScript Application Logic

document.addEventListener('DOMContentLoaded', function () {

    // Toggle favorite via AJAX
    document.querySelectorAll('.star-ajax-btn').forEach(button => {
        button.addEventListener('click', function (e) {
            e.preventDefault();
            const docId = this.getAttribute('data-id');
            const icon = this.querySelector('i');

            fetch(`/documents/favorite-ajax/${docId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    if (data.isFavorite) {
                        icon.classList.remove('bi-star');
                        icon.classList.add('bi-star-fill');
                        this.classList.add('starred');
                    } else {
                        icon.classList.remove('bi-star-fill');
                        icon.classList.add('bi-star');
                        this.classList.remove('starred');
                    }
                }
            })
            .catch(err => console.error('Error toggling favorite:', err));
        });
    });

    // Load document detail in Offcanvas
    const detailOffcanvasEl = document.getElementById('detailOffcanvas');
    if (detailOffcanvasEl) {
        const bsOffcanvas = new bootstrap.Offcanvas(detailOffcanvasEl);

        document.querySelectorAll('.btn-view-detail').forEach(button => {
            button.addEventListener('click', function (e) {
                e.preventDefault();
                const docId = this.getAttribute('data-id');
                loadDocumentDetail(docId, bsOffcanvas);
            });
        });
    }

    function loadDocumentDetail(docId, offcanvasInstance) {
        fetch(`/documents/${docId}`)
            .then(res => res.json())
            .then(data => {
                document.getElementById('detailTitle').textContent = data.title;
                document.getElementById('detailSubject').textContent = data.subjectName + ' (' + data.subjectCode + ')';
                document.getElementById('detailCategory').textContent = data.category;
                document.getElementById('detailAuthor').textContent = data.author || 'Chưa rõ tác giả';
                document.getElementById('detailFormat').textContent = data.fileExtension;
                document.getElementById('detailSize').textContent = data.fileSize;
                const sizeText = document.getElementById('detailSizeText');
                if (sizeText) {
                    sizeText.textContent = data.fileSize;
                }
                document.getElementById('detailCreated').textContent = data.createdAt;
                document.getElementById('detailDownloads').textContent = data.downloadCount;
                document.getElementById('detailViews').textContent = data.viewCount;
                document.getElementById('detailDescription').textContent = data.description || 'Không có mô tả chi tiết.';

                const tagsContainer = document.getElementById('detailTags');
                tagsContainer.innerHTML = '';
                if (data.tags) {
                    const tagList = data.tags.split(' ');
                    tagList.forEach(t => {
                        if (t.trim()) {
                            const badge = document.createElement('span');
                            badge.className = 'badge bg-light text-primary border me-1 mb-1';
                            badge.textContent = t.trim();
                            tagsContainer.appendChild(badge);
                        }
                    });
                }

                // Handle URL display
                const urlContainer = document.getElementById('detailUrlContainer');
                const urlLink = document.getElementById('detailUrlLink');
                const urlOpenBtn = document.getElementById('detailUrlBtn');

                if (data.fileUrl && data.fileUrl.trim() !== '') {
                    urlLink.href = data.fileUrl;
                    urlLink.textContent = data.fileUrl;
                    urlContainer.classList.remove('d-none');
                    urlOpenBtn.href = data.fileUrl;
                    urlOpenBtn.classList.remove('d-none');
                } else {
                    urlContainer.classList.add('d-none');
                    urlOpenBtn.classList.add('d-none');
                }

                // Action buttons
                const aiChatBtn = document.getElementById('detailAiChatBtn');
                if (aiChatBtn) {
                    aiChatBtn.href = `/ai/chat?docId=${data.id}`;
                }
                document.getElementById('detailDownloadBtn').href = `/documents/download/${data.id}`;
                document.getElementById('detailEditBtn').href = `/documents/edit/${data.id}`;
                document.getElementById('detailDeleteForm').action = `/documents/delete/${data.id}`;

                offcanvasInstance.show();
            })
            .catch(err => {
                console.error('Failed to load document details:', err);
                alert('Không thể tải thông tin chi tiết của tài liệu.');
            });
    }
});

