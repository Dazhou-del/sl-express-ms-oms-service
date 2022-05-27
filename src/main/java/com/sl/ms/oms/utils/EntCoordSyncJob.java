package com.sl.ms.oms.utils;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

/**
 * @ClassName: EntCoordSyncJob
 * @Description: 根据地理位置获取坐标
 */
@Slf4j
public class EntCoordSyncJob {
    static String AK = "MVnGC6qGSlY1i0iTv3Gbo9zsG6O94bex"; // 百度地图密钥

    public static void main(String[] args) {
//        String dom = "北京金燕龙";
//        String coordinate = getCoordinate(dom);
//        System.out.println("'" + dom + "'的经纬度为：" + coordinate);
        // System.err.println("######同步坐标已达到日配额6000限制，请明天再试！#####");
        //String begin = EntCoordSyncJob.getCoordinate("北京市昌平区建材城西路育新地铁站");
        //String end = EntCoordSyncJob.getCoordinate("新惠镇");
//        int time = getTime(begin, end);
//        double distance = getDistance(begin, end);
//        System.out.println("时间-->" + time / 60 + "分钟--距离-->" + distance / 1000 + "km");
        // Map map = getLocationByPosition(begin);
        //System.out.println(map);

//        List<Map> list = JSONArray.parseArray("[{\"lng\":116.302691,\"lat\":40.108076},{\"lng\":116.487527,\"lat\":40.121098},{\"lng\":116.485227,\"lat\":40.051324},{\"lng\":116.466542,\"lat\":40.034974},{\"lng\":116.459356,\"lat\":40.024145},{\"lng\":116.441533,\"lat\":40.031659},{\"lng\":116.336324,\"lat\":40.02967},{\"lng\":116.290618,\"lat\":40.099687}]", Map.class);
//        boolean flag = isPoint(list, 116.353776,40.066269);
//
//        System.out.println(flag);
//
//        Point2D.Double pd = new Point2D.Double();
//        pd.setLocation(116.353776,40.066269);
//
//        List<Point2D.Double> pds = list.stream().map(item -> {
//            Point2D.Double pdi = new Point2D.Double();
//            pdi.setLocation(Double.parseDouble(item.get("lng").toString()), Double.parseDouble(item.get("lat").toString()));
//            return pdi;
//        }).collect(Collectors.toList());
//
//        System.out.println(isInPolygon(pd,pds));
    }

    /**
     * 判断点是否在多边形内
     *
     * @param point 测试点
     * @param pts   多边形的点
     * @return boolean
     * @throws
     * @Title: IsPointInPoly
     * @Description: TODO()
     */
    public static boolean isInPolygon(Point2D.Double point, List<Point2D.Double> pts) {

        int N = pts.size();
        boolean boundOrVertex = true;
        int intersectCount = 0;//交叉点数量
        double precision = 2e-10; //浮点类型计算时候与0比较时候的容差
        Point2D.Double p1, p2;//临近顶点
        Point2D.Double p = point; //当前点

        p1 = pts.get(0);
        for (int i = 1; i <= N; ++i) {
            if (p.equals(p1)) {
                return boundOrVertex;
            }

            p2 = pts.get(i % N);
            if (p.x < Math.min(p1.x, p2.x) || p.x > Math.max(p1.x, p2.x)) {
                p1 = p2;
                continue;
            }

            //射线穿过算法
            if (p.x > Math.min(p1.x, p2.x) && p.x < Math.max(p1.x, p2.x)) {
                if (p.y <= Math.max(p1.y, p2.y)) {
                    if (p1.x == p2.x && p.y >= Math.min(p1.y, p2.y)) {
                        return boundOrVertex;
                    }

                    if (p1.y == p2.y) {
                        if (p1.y == p.y) {
                            return boundOrVertex;
                        } else {
                            ++intersectCount;
                        }
                    } else {
                        double xinters = (p.x - p1.x) * (p2.y - p1.y) / (p2.x - p1.x) + p1.y;
                        if (Math.abs(p.y - xinters) < precision) {
                            return boundOrVertex;
                        }

                        if (p.y < xinters) {
                            ++intersectCount;
                        }
                    }
                }
            } else {
                if (p.x == p2.x && p.y <= p2.y) {
                    Point2D.Double p3 = pts.get((i + 1) % N);
                    if (p.x >= Math.min(p1.x, p3.x) && p.x <= Math.max(p1.x, p3.x)) {
                        ++intersectCount;
                    } else {
                        intersectCount += 2;
                    }
                }
            }
            p1 = p2;
        }
        if (intersectCount % 2 == 0) {//偶数在多边形外
            return false;
        } else { //奇数在多边形内
            return true;
        }
    }

    // 调用百度地图API根据地址，获取坐标
    public static String getCoordinate(String address) {
        if (address != null && !"".equals(address)) {
            address = address.replaceAll("\\s*", "").replace("#", "栋");
            String url = "http://api.map.baidu.com/geocoding/v3/?output=json&ak=" + AK + "&callback=showLocation&address=" + address;
//            String url = "http://api.map.baidu.com/geocoder/v2/?address=" + address + "&output=json&ak=" + AK;
            String json = loadJSON(url);
            json = StringUtils.substringBetween(json, "showLocation(", ")");
            if (json != null && !"".equals(json)) {
                Map map = JSONUtil.toBean(json, Map.class);
                if ("0".equals(map.getOrDefault("status", "500").toString())) {
                    Map childMap = (Map) map.get("result");
                    Map posMap = (Map) childMap.get("location");
                    double lng = Double.parseDouble(posMap.getOrDefault("lng", "0").toString()); // 经度
                    double lat = Double.parseDouble(posMap.getOrDefault("lat", "0").toString()); // 纬度
                    DecimalFormat df = new DecimalFormat("#.######");
                    String lngStr = df.format(lng);
                    String latStr = df.format(lat);
                    return lngStr + "," + latStr;
                }
            }
        }
        return null;
    }


    public static Integer getTime(String origin, String destination) {
        String[] originArray = origin.split(",");
        String[] destinationArray = destination.split(",");
        origin = originArray[1] + "," + originArray[0];
        destination = destinationArray[1] + "," + destinationArray[0];
        String url = "http://api.map.baidu.com/directionlite/v1/driving?origin=" + origin + "&destination=" + destination + "&ak=" + AK;
        String json = loadJSON(url);
        if (json != null && !"".equals(json)) {
            Map map = JSONUtil.toBean(json, Map.class);
            if ("0".equals(map.getOrDefault("status", "500").toString())) {
                Map childMap = (Map) map.get("result");
                JSONArray jsonArray = (JSONArray) childMap.get("routes");
                JSONObject jsonObject = (JSONObject) jsonArray.get(0);
                Map posMap = (Map) jsonObject.get("routes");
                int duration = Integer.parseInt(jsonObject.get("duration") == null ? "0" : jsonObject.get("duration").toString());
                return duration;
            }
        }

        return null;
    }

    public static Double getDistance(String origin, String destination) {
        String[] originArray = origin.split(",");
        String[] destinationArray = destination.split(",");
        origin = originArray[1] + "," + originArray[0];
        destination = destinationArray[1] + "," + destinationArray[0];
        String url = "http://api.map.baidu.com/directionlite/v1/driving?origin=" + origin + "&destination=" + destination + "&ak=" + AK;
        String json = loadJSON(url);
        if (json != null && !"".equals(json)) {
            Map map = JSONUtil.toBean(json, Map.class);
            if ("0".equals(map.getOrDefault("status", "500").toString())) {
                Map childMap = (Map) map.get("result");
                JSONArray jsonArray = (JSONArray) childMap.get("routes");
                JSONObject jsonObject = (JSONObject) jsonArray.get(0);
                Map posMap = (Map) jsonObject.get("routes");
                double distance = Double.parseDouble(jsonObject.get("distance") == null ? "0" : jsonObject.get("distance").toString());
                return distance;
            }
        }

        return null;
    }

    public static String loadJSON(String url) {
        StringBuilder json = new StringBuilder();
        try {
            URL oracle = new URL(url);
            URLConnection yc = oracle.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream(), "UTF-8"));
            String inputLine = null;
            while ((inputLine = in.readLine()) != null) {
                json.append(inputLine);
            }
            in.close();
        } catch (MalformedURLException e) {
        } catch (IOException e) {
        }
        return json.toString();
    }

    /**
     * @param location
     * @return map
     * formatted_address:结构化地址信息
     * "country":"国家",
     * "country_code":国家编码,
     * "country_code_iso":"国家英文缩写（三位）",
     * "country_code_iso2":"国家英文缩写（两位）",
     * "province":"省名",
     * "city":"城市名",
     * "city_level":城市所在级别（仅国外有参考意义。国外行政区划与中国有差异，城市对应的层级不一定为『city』。country、province、city、district、town分别对应0-4级，若city_level=3，则district层级为该国家的city层级）,
     * "district":"区县名",
     * "town":"乡镇名",
     * "town_code":"乡镇id",
     * "adcode":"行政区划代码",
     * "street":"街道名（行政区划中的街道层级）",
     * "street_number":"街道门牌号",
     * "direction":"和当前坐标点的方向",
     * "distance":"离坐标点距离"
     */
    public static Map getLocationByPosition(String location) {
        String[] originArray = location.split(",");
        location = originArray[1] + "," + originArray[0];
        String url = "http://api.map.baidu.com/reverse_geocoding/v3/?ak=" + AK + "&output=json&coordtype=wgs84ll&location=" + location;
//        String url = "http://api.map.baidu.com/directionlite/v1/driving?origin=" + origin + "&destination=" + destination + "&ak=" + AK;
        String json = loadJSON(url);
        if (json != null && !"".equals(json)) {
            Map map = JSONUtil.toBean(json, Map.class);
            if ("0".equals(map.getOrDefault("status", "500").toString())) {
                Map childMap = (Map) map.get("result");
                Map areaMap = (Map) childMap.get("addressComponent");
                areaMap.put("formatted_address", childMap.getOrDefault("formatted_address", "").toString());
                return areaMap;
            }
        }

        return null;
    }


    /**
     * 判断点是否在区域内
     *
     * @param polygon   区域经纬度集合
     * @param longitude 经度
     * @param latitude  纬度
     * @return 返回true跟false
     */
    public static boolean isPoint(List<Map<String, String>> polygon, double longitude, double latitude) {

        Path2D.Double generalPath = new Path2D.Double();

        //获取第一个起点经纬度的坐标

        Map first = polygon.get(0);

        //通过移动到以double精度指定的指定坐标，把第一个起点添加到路径中
        generalPath.moveTo(Double.parseDouble(first.getOrDefault("lng", "").toString()), Double.parseDouble(first.getOrDefault("lat", "").toString()));

        //把集合中的第一个点删除防止重复添加

        polygon.remove(0);

        //循环集合里剩下的所有经纬度坐标

        for (Map d : polygon) {

            //通过从当前坐标绘制直线到以double精度指定的新指定坐标，将路径添加到路径。

            //从第一个点开始，不断往后绘制经纬度点

            generalPath.lineTo(Double.parseDouble(d.getOrDefault("lng", "").toString()), Double.parseDouble(d.getOrDefault("lat", "").toString()));

        }

        // 最后要多边形进行封闭，起点及终点

        generalPath.lineTo(Double.parseDouble(first.getOrDefault("lng", "").toString()), Double.parseDouble(first.getOrDefault("lat", "").toString()));

        //将直线绘制回最后一个 moveTo的坐标来关闭当前子路径。

        generalPath.closePath();

        //true如果指定的坐标在Shape边界内; 否则为false 。

        boolean res = generalPath.contains(longitude, latitude);
        log.info("电子围栏范围计算:{},longitude:{},latitude:{} \npolygon:{}", res, longitude, latitude, polygon);
        return res;
    }

}
