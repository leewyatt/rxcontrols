## RXControls

- RXControls Version 8.x.y need javafx8
- RXControls Version 11.x.y need javafx11+

一个javafx的自定义组件库, 密码可见组件, 轮播图组件, 动态按钮组件等, 音频频谱可视化组件,歌词组件 等... <br />

Javafx custom component library, password visible component, carousel component, dynamic button component,spectrum
component,lyrics component etc...  <br />


> QQ: **9670453**  <br />
> JavaFX QQ群: **518914410** <br/>


主要变化: RXCarousel 去掉SubScene (css name: .carousel-subscene) 
    考虑到一个界面可能有多个轮播图,避免层级过多; 
    如果想要部分的专场效果,有3D的感觉, 那么可以在Scene里设置; 
Main changes: RXCarousel removes SubScene (css name: .carousel-subscene) 
    Considering that an interface may have multiple carousels to avoid too many layers; 
    if you want some special effects and a 3D feeling, Need to set a perspective camera for setting the main scene graph;

Maven dependency

```xml

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
<dependency>
    <groupId>com.github.leewyatt</groupId>
    <artifactId>rxcontrols</artifactId>
    <!--   8.x.y         -->
    <version>8.0.3-alpha</version>
</dependency>
</dependencies>
```

- Carousel & FillButton inspired by [Gleidson28](https://github.com/Gleidson28)
- Soft Page Transition inspired by [Yuichi.Sakuraba](https://gist.github.com/skrb/1c62b77ef7ddb3c7adf4)
- Carousel inspired by [Swiper中文网](https://www.swiper.com.cn/)

> Demos in the test directory.

![](src/test/resources/screenshot/img6.png)
![](src/test/resources/screenshot/img1.png)
![](src/test/resources/screenshot/img2.png)
![](src/test/resources/screenshot/img3.png)
![](src/test/resources/screenshot/img4.png)
![](src/test/resources/screenshot/img5.png)