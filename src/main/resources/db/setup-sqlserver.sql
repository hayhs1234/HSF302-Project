-- ===================================================================
-- Script tạo Database và bảng cho StudyHub trên Microsoft SQL Server
-- ===================================================================

IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'StudyHubDB')
BEGIN
    CREATE DATABASE StudyHubDB;
END
GO

USE StudyHubDB;
GO

-- 2. Tạo bảng subjects (Môn học / Học phần)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'subjects')
BEGIN
    CREATE TABLE subjects (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        code VARCHAR(20) NOT NULL UNIQUE,
        name NVARCHAR(150) NOT NULL,
        description NVARCHAR(500),
        color VARCHAR(20) DEFAULT '#0d6efd',
        icon VARCHAR(50) DEFAULT 'bi-book',
        created_at DATETIME2 DEFAULT GETDATE()
    );
END
GO

-- 3. Tạo bảng documents (Tài liệu học tập)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'documents')
BEGIN
    CREATE TABLE documents (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        title NVARCHAR(255) COLLATE Latin1_General_CI_AI NOT NULL,
        file_name NVARCHAR(255),
        file_path NVARCHAR(500),
        file_url NVARCHAR(1000),
        file_extension VARCHAR(10) NOT NULL,
        file_size_formatted VARCHAR(20),
        file_size_bytes BIGINT DEFAULT 0,
        category NVARCHAR(100) NOT NULL,
        author NVARCHAR(150) COLLATE Latin1_General_CI_AI,
        description NVARCHAR(4000) COLLATE Latin1_General_CI_AI,
        tags NVARCHAR(255) COLLATE Latin1_General_CI_AI,
        is_favorite BIT DEFAULT 0,
        download_count INT DEFAULT 0,
        view_count INT DEFAULT 0,
        subject_id BIGINT,
        created_at DATETIME2 DEFAULT GETDATE(),
        updated_at DATETIME2 DEFAULT GETDATE(),
        CONSTRAINT FK_documents_subject FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE SET NULL
    );
END
GO

-- Bổ sung cột file_url nếu bảng đã tạo từ trước
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('documents') AND name = 'file_url')
BEGIN
    ALTER TABLE documents ADD file_url NVARCHAR(1000);
END
GO

-- Bổ sung các cột phục vụ kho tài liệu chia sẻ
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('documents') AND name = 'user_id')
BEGIN
    ALTER TABLE documents ADD user_id BIGINT;
    ALTER TABLE documents ADD CONSTRAINT FK_documents_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('documents') AND name = 'is_shared')
BEGIN
    ALTER TABLE documents ADD is_shared BIT DEFAULT 0;
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('documents') AND name = 'shared_at')
BEGIN
    ALTER TABLE documents ADD shared_at DATETIME2;
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('documents') AND name = 'shared_by_name')
BEGIN
    ALTER TABLE documents ADD shared_by_name NVARCHAR(150) COLLATE Latin1_General_CI_AI;
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('documents') AND name = 'shared_note')
BEGIN
    ALTER TABLE documents ADD shared_note NVARCHAR(500) COLLATE Latin1_General_CI_AI;
END
GO

-- 4. Tạo bảng users (Người dùng / Sinh viên)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'users')
BEGIN
    CREATE TABLE users (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        full_name NVARCHAR(150) NOT NULL,
        email NVARCHAR(150) NOT NULL UNIQUE,
        password VARCHAR(255) NOT NULL,
        role VARCHAR(50) DEFAULT 'STUDENT',
        created_at DATETIME2 DEFAULT GETDATE()
    );
END
GO

-- Bổ sung cột index_status và indexed_at phục vụ lập chỉ mục RAG
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('documents') AND name = 'index_status')
BEGIN
    ALTER TABLE documents ADD index_status VARCHAR(30) DEFAULT 'PENDING';
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('documents') AND name = 'indexed_at')
BEGIN
    ALTER TABLE documents ADD indexed_at DATETIME2;
END
GO

-- 5. Tạo bảng document_chunks (Lưu trữ phân đoạn văn bản phục vụ RAG BM25)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'document_chunks')
BEGIN
    CREATE TABLE document_chunks (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        document_id BIGINT NOT NULL,
        chunk_index INT NOT NULL,
        content NVARCHAR(MAX) NOT NULL,
        token_count INT DEFAULT 0,
        page_number INT NULL,
        created_at DATETIME2 DEFAULT GETDATE(),
        CONSTRAINT FK_document_chunks_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
    );
    CREATE INDEX idx_doc_chunk_doc_id ON document_chunks(document_id);
END
GO

-- Đồng bộ schema document_chunks nếu bảng đã tạo từ phiên bản cũ
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.document_chunks') AND name = 'page_number')
BEGIN
    ALTER TABLE dbo.document_chunks ADD page_number INT NULL;
END
GO

IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.document_chunks') AND name = 'normalized_content')
BEGIN
    DECLARE @dropSql NVARCHAR(MAX) = N'';
    SELECT @dropSql = @dropSql + N'ALTER TABLE dbo.document_chunks DROP CONSTRAINT ' + QUOTENAME(dc.name) + N'; '
    FROM sys.default_constraints dc
    JOIN sys.columns c ON dc.parent_object_id = c.object_id AND dc.parent_column_id = c.column_id
    WHERE dc.parent_object_id = OBJECT_ID('dbo.document_chunks') AND c.name = 'normalized_content';

    IF LEN(@dropSql) > 0
        EXEC sp_executesql @dropSql;

    ALTER TABLE dbo.document_chunks DROP COLUMN normalized_content;
END
GO

-- 6. Tạo bảng chat_conversations (Phiên hội thoại AI đa lượt)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'chat_conversations')
BEGIN
    CREATE TABLE chat_conversations (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id BIGINT NOT NULL,
        title NVARCHAR(255) NOT NULL,
        mode VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
        selected_document_id BIGINT NULL,
        created_at DATETIME2 DEFAULT GETDATE(),
        updated_at DATETIME2 DEFAULT GETDATE(),
        CONSTRAINT FK_chat_conversations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );
    CREATE INDEX idx_chat_conv_user_updated ON chat_conversations(user_id, updated_at DESC);
END
GO

-- 7. Tạo bảng chat_messages (Tin nhắn chi tiết & nguồn trích dẫn JSON trong phiên hội thoại)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'chat_messages')
BEGIN
    CREATE TABLE chat_messages (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        conversation_id BIGINT NOT NULL,
        role VARCHAR(20) NOT NULL,
        content NVARCHAR(MAX) NOT NULL,
        mode VARCHAR(20),
        provider VARCHAR(50),
        sources_json NVARCHAR(MAX) NULL,
        created_at DATETIME2 DEFAULT GETDATE(),
        CONSTRAINT FK_chat_messages_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE
    );
    CREATE INDEX idx_chat_msg_conv_id ON chat_messages(conversation_id);
END
GO

