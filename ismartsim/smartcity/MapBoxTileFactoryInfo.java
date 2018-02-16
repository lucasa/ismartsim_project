


import org.jxmapviewer.viewer.TileFactoryInfo;

/**
 * Uses OpenStreetMap
 * @author Martin Dummer
 */
public class MapBoxTileFactoryInfo extends TileFactoryInfo
{
	private static final int max = 22;

	/**
	 * Default constructor
	 */
	public MapBoxTileFactoryInfo()
	{
		this("MapBox", "https://api.tiles.mapbox.com/v4/mapbox.dark/");
	}

	public MapBoxTileFactoryInfo(String name, String baseURL)
	{
		super(name, 
				0, max - 2, max, 
				256, true, true, 					// tile size is 256 and x/y orientation is normal
				baseURL,
				"z", "x", "y");						// 5/15/10.png
	}

	@Override
	public String getTileUrl(int x, int y, int zoom)
	{
		zoom = max - zoom;
		String url = this.baseURL + zoom + "/" + x + "/" + y + ".png?access_token=pk.eyJ1IjoibHVjYXNhbGJlcnRvIiwiYSI6ImNqMjJzNXJhMDAwb2oycXBoM3U5dnJoNXgifQ._15PM6hiSZcyTa-Yz_ew-g";
		return url;
	}

}
