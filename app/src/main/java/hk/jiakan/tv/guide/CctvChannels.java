package hk.jiakan.tv.guide;

public final class CctvChannels {
    private CctvChannels() {}
    public static String code(String id) {
        if(id==null) return "";
        if("CCTV4K.cn".equals(id)) return "cctv4k";
        if("CCTV8K.cn".equals(id)) return "cctv8k";
        if("CCTV4.cn".equals(id)) return "cctv4";
        if("CCTV4Europe.cn".equals(id)) return "cctveurope";
        if("CCTV9.cn".equals(id)) return "cctvjilu";
        if("CCTV14.cn".equals(id)) return "cctvchild";
        if("CCTV5Plus.cn".equals(id)) return "cctv5plus";
        if(id.matches("CCTV([1-9]|1[0-7])\\.cn")) return id.substring(0,id.length()-3).toLowerCase(java.util.Locale.ROOT);
        return "";
    }
    /** Exact EPG.pw listings, including distinct Asian and European editions. */
    public static String backupId(String id) {
        String code=code(id);
        switch(code) {
            case "cctv1":return "545932";case "cctv2":return "545933";
            case "cctv3":return "545934";case "cctv4":return "545935";
            case "cctv5":return "545936";case "cctv5plus":return "545937";
            case "cctv6":return "545938";case "cctv7":return "545939";
            case "cctv8":return "545940";case "cctvjilu":return "545941";
            case "cctv10":return "545942";case "cctv11":return "545943";
            case "cctv12":return "545944";case "cctv13":return "545945";
            case "cctvchild":return "545946";case "cctv15":return "545947";
            case "cctv16":return "545948";case "cctv17":return "545949";
            case "cctv8k":return "545950";case "cctveurope":return "545951";
            case "cctv4k":return "539634";default:return "";
        }
    }
}
