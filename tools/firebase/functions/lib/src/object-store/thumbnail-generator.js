"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.generateThumbnail = generateThumbnail;
exports.isThumbnailable = isThumbnailable;
const sharp_1 = __importDefault(require("sharp"));
/**
 * Firebase pendant of `ThumbnailGenerator`, which uses Thumbnailator with the same
 * max-dimension box, JPEG output and quality. `rotate()` applies the EXIF orientation
 * so portrait photos are not thumbnailed sideways, and `withoutEnlargement` leaves
 * images already smaller than the box alone instead of upscaling them.
 */
async function generateThumbnail(source, maxDimension, quality) {
    return (0, sharp_1.default)(source)
        .rotate()
        .resize({ width: maxDimension, height: maxDimension, fit: 'inside', withoutEnlargement: true })
        .jpeg({ quality: Math.round(quality * 100) })
        .toBuffer();
}
/** SVG is excluded for the same reason as in `UploadObject`: it is not a raster source. */
function isThumbnailable(mimeType) {
    if (!mimeType)
        return false;
    const normalized = mimeType.toLowerCase();
    return normalized.startsWith('image/') && normalized !== 'image/svg+xml';
}
//# sourceMappingURL=thumbnail-generator.js.map