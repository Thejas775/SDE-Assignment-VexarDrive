//
//  Page.swift
//  Fleet Management Test
//

import Foundation

/// The envelope every paginated list endpoint returns.
///
/// `GET /trips/{id}/route` is the one exception - it returns a plain array.
struct Page<Item: Decodable & Sendable>: Decodable, Sendable {
    let items: [Item]
    let total: Int
    let page: Int
    let pageSize: Int
    let pages: Int

    /// Whether a page after this one exists. Drives infinite scroll.
    var hasMorePages: Bool { page < pages }

    /// `pages` is 0 when nothing matched, so `total` is the honest emptiness check.
    var isEmpty: Bool { total == 0 }
}
