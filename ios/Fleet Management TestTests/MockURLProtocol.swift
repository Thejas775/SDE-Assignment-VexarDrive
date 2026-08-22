//
//  MockURLProtocol.swift
//  Fleet Management TestTests
//

import Foundation

/// Answers requests from a handler instead of opening a socket, and records
/// every request so a test can assert on headers and query strings.
///
/// No test in this bundle touches the network.
final class MockURLProtocol: URLProtocol, @unchecked Sendable {

    private static let lock = NSLock()
    nonisolated(unsafe) private static var _handler: ((URLRequest) -> (Int, Data))?
    nonisolated(unsafe) private static var _requests: [URLRequest] = []

    /// Installs a handler and clears the recorded requests.
    static func respond(with handler: @escaping (URLRequest) -> (Int, Data)) {
        lock.withLock {
            _handler = handler
            _requests = []
        }
    }

    static func reset() {
        lock.withLock {
            _handler = nil
            _requests = []
        }
    }

    static var requests: [URLRequest] { lock.withLock { _requests } }

    static func requests(matching suffix: String) -> [URLRequest] {
        requests.filter { $0.url?.path.hasSuffix(suffix) == true }
    }

    /// A URLSession wired to this protocol and nothing else.
    static func makeSession() -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        return URLSession(configuration: configuration)
    }

    // MARK: - URLProtocol

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func stopLoading() {}

    override func startLoading() {
        Self.lock.withLock { Self._requests.append(request) }

        guard let handler = Self.lock.withLock({ Self._handler }) else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }

        let (status, data) = handler(request)
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: status,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }
}

/// Fails every request the way a stopped backend does.
final class OfflineURLProtocol: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func stopLoading() {}
    override func startLoading() {
        client?.urlProtocol(self, didFailWithError: URLError(.cannotConnectToHost))
    }

    static func makeSession() -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [OfflineURLProtocol.self]
        return URLSession(configuration: configuration)
    }
}

extension URLRequest {
    /// URLProtocol receives the body as a stream, so `httpBody` is nil there.
    /// Tests that assert on what was sent need this.
    var capturedBody: Data? {
        if let httpBody { return httpBody }
        guard let stream = httpBodyStream else { return nil }

        stream.open()
        defer { stream.close() }

        var data = Data()
        let size = 4096
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: size)
        defer { buffer.deallocate() }

        while stream.hasBytesAvailable {
            let read = stream.read(buffer, maxLength: size)
            guard read > 0 else { break }
            data.append(buffer, count: read)
        }
        return data
    }
}
